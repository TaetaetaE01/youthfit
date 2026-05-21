package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.application.port.PeriodLlmDirectExtractor;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OpenAiPolicyPeriodExtractor implements PeriodLlmDirectExtractor {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPolicyPeriodExtractor.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            당신은 청년 정책 본문에서 신청 기간만 추출하는 파서입니다.
            반드시 아래 JSON 스키마로만 응답하세요.
            {"applyStart": "YYYY-MM-DD" | null, "applyEnd": "YYYY-MM-DD" | null}

            규칙:
            - 정확한 연/월/일이 확인될 때만 값을 채웁니다.
            - "사업기간", "운영기간", "수행기간", "교육기간", "지원기간"은
              신청기간이 아니므로 무시합니다.
            - "연중수시", "상시접수", "공고 시 별도 안내", "추후 공지" 등은 null.
            - 연도가 없는 기간("매년 3월~4월")은 null.
            - 본문에 없는 정보를 지어내지 마세요.
            - JSON 외의 텍스트를 출력하지 마세요.
            """;

    private final OpenAiPolicyPeriodProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient = RestClient.create();

    @Override
    public Optional<PeriodCandidate> extract(String title, String body) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) return Optional.empty();
        if (body == null || body.isBlank()) return Optional.empty();

        int limit = properties.getMaxBodyChars() > 0 ? properties.getMaxBodyChars() : body.length();
        String truncatedBody = body.length() > limit ? body.substring(0, limit) : body;
        String userMessage = "제목: " + (title == null ? "" : title) + "\n\n본문:\n" + truncatedBody;

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)));

        try {
            JsonNode response = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
                log.warn("기간 추출 응답이 비어 있습니다: title={}", title);
                return Optional.empty();
            }

            publishCostEvent(response);
            String content = response.get("choices").get(0).get("message").get("content").asText();
            return parseContent(content);
        } catch (RuntimeException e) {
            log.warn("LLM 기반 기간 추출 실패: title={}, cause={}", title, e.getMessage());
            return Optional.empty();
        }
    }

    private void publishCostEvent(JsonNode response) {
        try {
            JsonNode usage = response.get("usage");
            int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
            int completion = usage == null || !usage.has("completion_tokens") ? 0 : usage.get("completion_tokens").asInt();
            eventPublisher.publishEvent(new LlmCallRecorded(
                    LlmModule.INGESTION, properties.getModel(), prompt, completion, Instant.now()));
        } catch (Exception e) {
            log.warn("ingestion LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
        }
    }

    private Optional<PeriodCandidate> parseContent(String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonNode node = objectMapper.readTree(json);
            LocalDate start = parseDate(node.get("applyStart"));
            LocalDate end = parseDate(node.get("applyEnd"));
            if (start == null && end == null) return Optional.empty();
            if (start != null && end != null && end.isBefore(start)) return Optional.empty();
            return Optional.of(new PeriodCandidate(start, end, PeriodSource.LLM_DIRECT, 0.60, "llm-direct"));
        } catch (JacksonException e) {
            log.warn("기간 JSON 파싱 실패: payload={}", json);
            return Optional.empty();
        }
    }

    private LocalDate parseDate(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String text = node.asText();
        if (text == null || text.isBlank() || "null".equalsIgnoreCase(text)) return null;
        try { return LocalDate.parse(text); } catch (DateTimeException e) { return null; }
    }
}
