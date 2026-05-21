package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.application.port.PeriodLlmDisambiguator;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OpenAiPolicyPeriodDisambiguator implements PeriodLlmDisambiguator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPolicyPeriodDisambiguator.class);
    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            당신은 정책 본문에서 추출된 여러 신청기간 후보 중 정답을 선택하는 검수자입니다.
            본문 스니펫과 후보 목록을 보고, 어느 후보가 진짜 신청기간인지 ID로 응답하세요.

            반드시 아래 JSON 스키마로만 응답하세요.
            {"chosenId": <정수|null>, "confidence": <0.0-1.0>, "reasoning": "<한 줄>"}

            규칙:
            - "사업기간", "운영기간", "수행기간"으로 보이는 후보는 무시합니다.
            - 정답 후보가 없다면 chosenId = null.
            - confidence 는 본문 근거가 명확할수록 높게.
            - JSON 외의 텍스트를 출력하지 마세요.
            """;

    private final OpenAiPolicyPeriodProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient = RestClient.create();

    @Override
    public Optional<PeriodCandidate> choose(String bodySnippet, List<PeriodCandidate> candidates) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) return Optional.empty();
        if (candidates == null || candidates.size() < 2) return Optional.empty();

        String userMessage = "본문 스니펫:\n" + bodySnippet + "\n\n후보:\n" + serializeCandidates(candidates);

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)));

        try {
            JsonNode resp = restClient.post().uri(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(body).retrieve().body(JsonNode.class);
            if (resp == null || !resp.has("choices") || resp.get("choices").isEmpty()) return Optional.empty();
            publishCostEvent(resp);
            String content = resp.get("choices").get(0).get("message").get("content").asText();
            return parse(content, candidates);
        } catch (RuntimeException e) {
            log.warn("disambiguator 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String serializeCandidates(List<PeriodCandidate> cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.size(); i++) {
            PeriodCandidate c = cs.get(i);
            sb.append(i).append(". start=").append(c.start())
                    .append(" end=").append(c.end())
                    .append(" source=").append(c.source())
                    .append(" evidence=\"").append(c.evidence()).append("\"\n");
        }
        return sb.toString();
    }

    private Optional<PeriodCandidate> parse(String json, List<PeriodCandidate> cs) {
        try {
            JsonNode n = objectMapper.readTree(json);
            JsonNode idNode = n.get("chosenId");
            if (idNode == null || idNode.isNull()) return Optional.empty();
            int id = idNode.asInt(-1);
            if (id < 0 || id >= cs.size()) return Optional.empty();
            double conf = n.has("confidence") ? n.get("confidence").asDouble(0.7) : 0.7;
            PeriodCandidate chosen = cs.get(id);
            return Optional.of(new PeriodCandidate(
                    chosen.start(), chosen.end(),
                    PeriodSource.LLM_DISAMBIGUATED, conf,
                    chosen.evidence()));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    private void publishCostEvent(JsonNode resp) {
        try {
            JsonNode u = resp.get("usage");
            int p = u == null || !u.has("prompt_tokens") ? 0 : u.get("prompt_tokens").asInt();
            int c = u == null || !u.has("completion_tokens") ? 0 : u.get("completion_tokens").asInt();
            eventPublisher.publishEvent(new LlmCallRecorded(LlmModule.INGESTION, properties.getModel(), p, c, Instant.now()));
        } catch (Exception ignored) {}
    }
}
