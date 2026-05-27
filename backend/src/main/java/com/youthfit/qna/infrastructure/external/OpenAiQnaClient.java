package com.youthfit.qna.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.common.openai.OpenAiErrorClassifier;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.qna.application.dto.command.PolicyMetadata;
import com.youthfit.qna.application.port.QnaLlmProvider;
import io.github.resilience4j.retry.annotation.Retry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OpenAiQnaClient implements QnaLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiQnaClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            당신은 청년 정책 Q&A 전문가입니다.
            사용자가 특정 정책에 대해 질문하면, 제공된 정책 메타데이터와 본문 컨텍스트에 근거하여 답변하세요.

            규칙:
            - 본문 컨텍스트에 답이 있으면 본문을 우선 사용하세요.
            - 본문에 답이 없으면 정책 메타데이터로 보강하세요.
            - 메타데이터와 본문 어느 쪽에도 없는 내용을 지어내지 마세요.
            - 메타데이터와 본문 모두에 답이 없으면 "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다."라고 답변하세요.
            - 쉬운 한국어로 답변하세요.
            - 답변은 간결하고 핵심적으로 작성하세요.

            출력 형식 가이드:
            - 정보가 여러 항목이면 글머리 기호(`-`)로 정리하세요.
            - 핵심 수치(금액·기간·연령·횟수)는 굵게(`**...**`) 표시하세요.
            - 본문이 2개 이상의 명확한 섹션으로 나뉠 때만 `###` 헤더를 쓰세요.
              헤더 앞에 이모지를 1개까지 쓸 수 있으나, 정확히 맞는 경우에만.
            - 짧은 답이면 평문 한두 줄로도 충분합니다 — 형식을 위한 형식은 피하세요.
            """;

    private static final String FOLLOW_UP_SYSTEM_PROMPT = """
            당신은 청년 정책 후속 질문 제안 도우미입니다.
            방금 사용자에게 답변한 내용과 정책 본문 컨텍스트를 보고, 같은 정책 안에서 사용자가 이어 물어볼 만한 후속 질문 2~3개를 제안하세요.

            규칙:
            - 출력은 JSON 배열만 — 다른 텍스트·설명·코드펜스 금지.
            - 예: ["질문1", "질문2", "질문3"]
            - 본문 컨텍스트에서 실제로 답을 찾을 수 있는 질문만 제안하세요. 본문에 단서가 없는 질문은 절대 제안하지 마세요.
              (예: 본문에 신청 방법이 없으면 "온라인으로 신청 가능한가요?" 같은 질문 금지)
            - 일반론적 질문(처리 기간, 온라인 신청 가능 여부, 제출 방법 등)도 본문에 근거가 있을 때만 허용.
            - 이미 답변에 명확히 포함된 정보를 다시 묻지 마세요.
            - 같은 정책 범위 안에서만 — 다른 정책으로 넘어가는 질문 금지.
            - 본문 컨텍스트에서 답할 수 있는 질문이 없으면 빈 배열 [] 을 반환하세요.
            - 한국어, 자연스러운 의문문.
            """;

    private static final int FOLLOW_UP_MAX_TOKENS = 200;

    private static final ObjectMapper SHARED_PARSE_MAPPER = new ObjectMapper();

    private final OpenAiQnaProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final OpenAiErrorClassifier errorClassifier;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiQnaClient(
            OpenAiQnaProperties properties,
            ApplicationEventPublisher eventPublisher,
            OpenAiErrorClassifier errorClassifier,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.errorClassifier = errorClassifier;
        this.restClient = restClientBuilder.build();
    }

    @Override
    @Retry(name = "openai-qna")
    public String generateAnswer(String policyTitle, PolicyMetadata metadata, String context, String question, Consumer<String> chunkConsumer) {
        String userMessage = buildUserMessage(policyTitle, metadata, context, question);

        // temperature/seed 고정: 동일 정책·컨텍스트·질문에 대한 답변 변동 폭을 줄여 의미 캐시 미스 시에도
        // 같은 질문군이 거의 같은 답변을 받도록 한다.
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "temperature", 0.2,
                "seed", 1,
                "stream", true,
                "stream_options", Map.of("include_usage", true),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        InputStream inputStream;
        try {
            inputStream = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(InputStream.class);
        } catch (RestClientException e) {
            log.warn("OpenAI Q&A 호출 실패 (분류 → retry 판정): policyTitle={}, status={}, msg={}",
                    policyTitle,
                    e instanceof RestClientResponseException rce ? rce.getStatusCode() : "n/a",
                    e.getMessage());
            throw errorClassifier.classify(e);
        }

        if (inputStream == null) {
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "Q&A 답변 생성에 실패했습니다");
        }

        // 스트림 open 이후 파싱 실패는 이미 청크가 클라이언트에 전송됐을 수 있으므로
        // retry 하지 않고 즉시 실패 처리한다.
        try {
            return readStreamResponse(inputStream, chunkConsumer);
        } catch (Exception e) {
            log.error("OpenAI Q&A 스트리밍 응답 파싱 실패: policyTitle={}", policyTitle, e);
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "Q&A 답변 생성에 실패했습니다");
        }
    }

    @Override
    @Retry(name = "openai-qna")
    public List<String> generateFollowUpQuestions(String policyTitle, String question, String answer, String context) {
        String safeContext = (context == null || context.isBlank()) ? "(본문 컨텍스트 없음)" : context;
        String userMessage = "정책명: " + policyTitle
                + "\n\n사용자 질문: " + question
                + "\n\n방금 답변:\n" + answer
                + "\n\n정책 본문 컨텍스트(이 안에서 답할 수 있는 질문만 제안):\n" + safeContext;

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", FOLLOW_UP_MAX_TOKENS,
                "temperature", 0.3,
                "messages", List.of(
                        Map.of("role", "system", "content", FOLLOW_UP_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("OpenAI follow-up 호출 실패 (분류 → retry 판정): policyTitle={}, status={}, msg={}",
                    policyTitle,
                    e instanceof RestClientResponseException rce ? rce.getStatusCode() : "n/a",
                    e.getMessage());
            throw errorClassifier.classify(e);
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) return List.of();
            String content = choices.get(0).path("message").path("content").asText("");

            // 비용 메트릭 발행
            JsonNode usage = root.get("usage");
            int promptTokens = usage != null ? usage.path("prompt_tokens").asInt(0) : 0;
            int completionTokens = usage != null ? usage.path("completion_tokens").asInt(0) : 0;
            try {
                eventPublisher.publishEvent(new LlmCallRecorded(
                        LlmModule.QNA, properties.getModel(), promptTokens, completionTokens, Instant.now()
                ));
            } catch (Exception e) {
                log.warn("qna follow-up LLM 비용 이벤트 발행 실패", e);
            }

            return parseFollowUps(content, objectMapper);
        } catch (Exception e) {
            log.warn("OpenAI follow-up 응답 파싱 실패: policyTitle={}, error={}", policyTitle, e.toString());
            return List.of();
        }
    }

    static List<String> parseFollowUps(String content) {
        return parseFollowUps(content, SHARED_PARSE_MAPPER);
    }

    static List<String> parseFollowUps(String content, ObjectMapper mapper) {
        if (content == null || content.isBlank()) return List.of();
        String trimmed = content.trim();
        // 코드펜스 제거
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
            trimmed = trimmed.trim();
        }
        if (!trimmed.startsWith("[")) return List.of();

        try {
            JsonNode arr = mapper.readTree(trimmed);
            if (!arr.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            for (JsonNode node : arr) {
                if (node != null && node.isString()) {
                    String s = node.asText();
                    if (!s.isBlank()) result.add(s);
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    static String buildUserMessage(String policyTitle, PolicyMetadata metadata, String context, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("정책명: ").append(policyTitle).append("\n\n");

        List<String> metaLines = new ArrayList<>();
        if (metadata != null) {
            if (metadata.category() != null)       metaLines.add("- 분야: " + metadata.category());
            if (metadata.summary() != null)        metaLines.add("- 요약: " + metadata.summary());
            if (metadata.supportTarget() != null)  metaLines.add("- 지원 대상: " + metadata.supportTarget());
            if (metadata.supportContent() != null) metaLines.add("- 지원 내용: " + metadata.supportContent());
            if (metadata.organization() != null)   metaLines.add("- 운영 기관: " + metadata.organization());
            if (metadata.contact() != null)        metaLines.add("- 문의처: " + metadata.contact());
            if (metadata.applyStart() != null && metadata.applyEnd() != null) {
                metaLines.add("- 신청 기간: " + metadata.applyStart() + " ~ " + metadata.applyEnd());
            }
            if (metadata.provideType() != null)    metaLines.add("- 지급 방식: " + metadata.provideType());
        }
        if (!metaLines.isEmpty()) {
            sb.append("정책 메타데이터:\n");
            for (String line : metaLines) sb.append(line).append("\n");
            sb.append("\n");
        }

        sb.append("정책 본문 컨텍스트:\n").append(context).append("\n\n");
        sb.append("질문: ").append(question);
        return sb.toString();
    }

    private String readStreamResponse(InputStream inputStream, Consumer<String> chunkConsumer) throws Exception {
        StringBuilder fullAnswer = new StringBuilder();
        int promptTokens = 0;
        int completionTokens = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }

                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }

                JsonNode node = objectMapper.readTree(data);

                // usage 청크 (choices 비어있음)
                JsonNode usage = node.get("usage");
                if (usage != null && !usage.isNull()) {
                    if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").asInt();
                    if (usage.has("completion_tokens")) completionTokens = usage.get("completion_tokens").asInt();
                    continue;
                }

                JsonNode choices = node.get("choices");
                if (choices == null || choices.isEmpty()) {
                    continue;
                }
                JsonNode delta = choices.get(0).get("delta");
                if (delta == null || !delta.has("content")) {
                    continue;
                }
                String content = delta.get("content").asText();
                if (content != null && !content.isEmpty()) {
                    fullAnswer.append(content);
                    chunkConsumer.accept(content);
                }
            }
        }

        // 스트림 종료 후 메트릭 발행 (usage 누락 시 0)
        try {
            eventPublisher.publishEvent(new LlmCallRecorded(
                    LlmModule.QNA, properties.getModel(), promptTokens, completionTokens, Instant.now()
            ));
        } catch (Exception e) {
            log.warn("qna LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
        }

        return fullAnswer.toString();
    }
}
