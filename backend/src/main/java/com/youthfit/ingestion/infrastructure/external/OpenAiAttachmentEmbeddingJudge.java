package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult.AttachmentDecision;
import com.youthfit.ingestion.application.port.AttachmentEmbeddingJudge;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiAttachmentEmbeddingJudge implements AttachmentEmbeddingJudge {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAttachmentEmbeddingJudge.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            당신은 청년 정책 첨부파일이 RAG 검색·Q&A 에 가치 있는지 판정하는 분류기입니다.
            각 첨부에 대해 임베딩 포함 여부를 판정하고, 반드시 아래 JSON 스키마로만 응답하세요.
            {"decisions": [{"attachmentId": <number>, "embed": <true|false>, "reason": "<한 줄 사유>"}]}

            판정 기준:
            - 포함(embed=true): 지원 대상·금액·일정·신청 절차·자격 요건 등 실질 정책 내용을 담은 첨부.
            - 제외(embed=false): 단순 서식, 신청서 양식, 동의서, 개인정보 수집·이용 동의, 반복되는 안내문·boilerplate.
            - 애매하면 포함(embed=true) 으로 판정하세요. (보수적)
            - 입력에 주어진 모든 attachmentId 에 대해 정확히 하나씩 판정을 출력하세요.
            - JSON 외의 텍스트를 출력하지 마세요.
            """;

    private final OpenAiAttachmentGateProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient;

    public OpenAiAttachmentEmbeddingJudge(
            OpenAiAttachmentGateProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public AttachmentEmbeddingResult judge(AttachmentEmbeddingJudgeCommand command) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("attachment-gate apiKey 미설정");
        }

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildUserMessage(command))));

        JsonNode response = restClient.post()
                .uri(CHAT_COMPLETIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            throw new IllegalStateException("attachment-gate 응답이 비어 있음");
        }
        publishCostEvent(response);
        String content = response.get("choices").get(0).get("message").get("content").asText();
        return parseContent(content);
    }

    String buildUserMessage(AttachmentEmbeddingJudgeCommand command) {
        int limit = properties.getMaxPreviewChars() > 0 ? properties.getMaxPreviewChars() : Integer.MAX_VALUE;
        StringBuilder sb = new StringBuilder();
        sb.append("정책 제목: ").append(command.policyTitle() == null ? "" : command.policyTitle()).append('\n');
        if (command.policySummary() != null && !command.policySummary().isBlank()) {
            sb.append("정책 요약: ").append(command.policySummary()).append('\n');
        }
        sb.append("\n아래 첨부들을 판정하세요:\n");
        for (var item : command.attachments()) {
            String preview = item.contentPreview() == null ? "" : item.contentPreview();
            if (preview.length() > limit) {
                preview = preview.substring(0, limit);
            }
            sb.append("\n--- attachmentId=").append(item.attachmentId())
                    .append(" name=\"").append(item.name()).append("\" ---\n")
                    .append(preview).append('\n');
        }
        return sb.toString();
    }

    private AttachmentEmbeddingResult parseContent(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode decisions = node.get("decisions");
            List<AttachmentDecision> out = new ArrayList<>();
            if (decisions != null && decisions.isArray()) {
                for (JsonNode d : decisions) {
                    if (!d.has("attachmentId")) continue;
                    out.add(new AttachmentDecision(
                            d.get("attachmentId").asLong(),
                            d.path("embed").asBoolean(true),
                            d.path("reason").asText("")));
                }
            }
            return new AttachmentEmbeddingResult(out);
        } catch (Exception e) {
            throw new IllegalStateException("attachment-gate JSON 파싱 실패: " + json, e);
        }
    }

    private void publishCostEvent(JsonNode response) {
        try {
            JsonNode usage = response.get("usage");
            int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
            int completion = usage == null || !usage.has("completion_tokens") ? 0 : usage.get("completion_tokens").asInt();
            eventPublisher.publishEvent(new LlmCallRecorded(
                    LlmModule.ATTACHMENT_GATE, properties.getModel(), prompt, completion, Instant.now()));
        } catch (Exception e) {
            log.warn("attachment-gate LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
        }
    }
}
