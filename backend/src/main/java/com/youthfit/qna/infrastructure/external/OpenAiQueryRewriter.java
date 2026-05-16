package com.youthfit.qna.infrastructure.external;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.qna.infrastructure.config.QueryRewriteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OpenAiQueryRewriter implements QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiQueryRewriter.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MIN_LENGTH = 5;
    private static final int MAX_LENGTH = 200;

    private static final String SYSTEM_PROMPT = """
            당신은 한국 청년 정책 문서 검색을 돕는 query 재작성 어시스턴트입니다.

            규칙:
            1. 사용자 질문을 정책 표준 용어로 변환하세요.
               예: "작년/올해" → "직전 N개월/최근/당해연도"
               예: "받을 수 있어?" → "지원 자격 / 신청 조건"
            2. 의미를 추측·확장하지 마세요. 동의어·표준 용어 변환만 허용.
            3. 정책명을 query 에 포함하세요.
            4. 100자 이내, 검색용 키워드 중심.
            5. 결과만 출력. 부가 설명 금지.
            """;

    private final QueryRewriteProperties properties;
    private final OpenAiQnaProperties qnaProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiQueryRewriter(
            QueryRewriteProperties properties,
            OpenAiQnaProperties qnaProperties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.properties = properties;
        this.qnaProperties = qnaProperties;
        this.eventPublisher = eventPublisher;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<String> rewrite(String policyTitle, String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return Optional.empty();
        }

        long startNanos = System.nanoTime();
        Map<String, Object> requestBody = Map.of(
                "model", properties.model(),
                "max_tokens", properties.maxTokens(),
                "temperature", properties.temperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildUserMessage(policyTitle, userQuestion))
                )
        );

        try {
            String responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + qnaProperties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            String content = (choices != null && !choices.isEmpty())
                    ? choices.get(0).path("message").path("content").asText("")
                    : "";

            JsonNode usage = root.get("usage");
            int promptTokens = usage != null ? usage.path("prompt_tokens").asInt(0) : 0;
            int completionTokens = usage != null ? usage.path("completion_tokens").asInt(0) : 0;
            try {
                eventPublisher.publishEvent(new LlmCallRecorded(
                        LlmModule.QUERY_REWRITE, properties.model(),
                        promptTokens, completionTokens, Instant.now()
                ));
            } catch (Exception e) {
                log.warn("query-rewrite LLM 비용 이벤트 발행 실패", e);
            }

            Optional<String> rewritten = parseRewritten(content);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            if (rewritten.isPresent()) {
                log.info("query rewrite: original=\"{}\", rewritten=\"{}\", duration={}ms",
                        userQuestion, rewritten.get(), durationMs);
            } else {
                log.info("query rewrite fallback: reason=too-short-or-empty, original=\"{}\", duration={}ms",
                        userQuestion, durationMs);
            }
            return rewritten;
        } catch (Exception e) {
            log.warn("query rewrite fallback: reason=exception, original=\"{}\", error={}",
                    userQuestion, e.toString());
            return Optional.empty();
        }
    }

    static Optional<String> parseRewritten(String content) {
        if (content == null) return Optional.empty();
        String trimmed = content.trim();
        if (trimmed.length() < MIN_LENGTH) return Optional.empty();
        if (trimmed.length() > MAX_LENGTH) trimmed = trimmed.substring(0, MAX_LENGTH);
        return Optional.of(trimmed);
    }

    static String buildUserMessage(String policyTitle, String userQuestion) {
        return "정책: " + policyTitle
                + "\n질문: " + userQuestion
                + "\n\n재작성된 검색 query:";
    }
}
