package com.youthfit.guide.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.model.GuidePitfall;
import com.youthfit.guide.domain.model.GuideSourceField;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiChatClient implements GuideLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            너는 한국 청년 정책 가이드를 만드는 보조자다.

            원칙:
            1. 입력으로 주어진 원문에만 근거하라. 원문에 없는 조건/금액/기한/자격을 추가하지 마라.
            2. 원문에 명시된 모든 금액·연령·기간·지역 조건은 풀이에 누락 없이 보존하라.
            3. 어려운 행정 용어를 일상어로 치환할 때 의미를 바꾸지 마라.
            4. 어조는 원문의 명사형/단정형을 유지하라. "~예요", "~드려요", "~해요" 같은 친근체는 절대 사용하지 마라.
            5. 추정/가정/예시/"~일 수 있어요" 같은 표현 금지.
            6. 환경값(중위소득 N% 등)은 입력의 referenceYear 기준 환산값을 괄호로 병기하되, 환산값이 입력에 없으면 만들어내지 마라.

            출력 단위:
            - oneLineSummary: 정책의 정체를 1~2문장으로. 누가/무엇을/어떻게 받는지 핵심만.
            - target: supportTarget 원문의 풀이 (불릿 항목 배열). 입력에 supportTarget이 비어있으면 null.
            - criteria: selectionCriteria 원문의 풀이. 입력에 selectionCriteria가 비어있으면 null.
            - content: supportContent 원문의 풀이. 입력에 supportContent가 비어있으면 null.
            - pitfalls: 사용자가 원문을 한 번 읽고 놓칠 만한 함정 항목. 각 항목에 sourceField (SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY) 라벨 필수.
            """;

    private final OpenAiChatProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GuideContent generateGuide(GuideGenerationInput input) {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildUserMessage(input))
                ),
                "response_format", buildResponseFormat()
        );

        JsonNode response = restClient.post()
                .uri(CHAT_COMPLETIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            log.error("OpenAI Chat API 호출 실패: policyId={}", input.policyId());
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 생성에 실패했습니다");
        }

        String json = response.get("choices").get(0).get("message").get("content").asText();
        log.info("가이드 생성 완료: policyId={}, 응답 길이={}", input.policyId(), json.length());
        return parseResponse(json);
    }

    GuideContent parseResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String oneLine = node.get("oneLineSummary").asText();
            GuidePairedSection target = parsePaired(node.get("target"));
            GuidePairedSection criteria = parsePaired(node.get("criteria"));
            GuidePairedSection content = parsePaired(node.get("content"));
            List<GuidePitfall> pitfalls = parsePitfalls(node.get("pitfalls"));
            return new GuideContent(oneLine, target, criteria, content, pitfalls);
        } catch (Exception e) {
            log.error("가이드 응답 JSON 파싱 실패: {}", json, e);
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 응답 파싱 실패");
        }
    }

    private GuidePairedSection parsePaired(JsonNode node) {
        if (node == null || node.isNull()) return null;
        JsonNode itemsNode = node.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) return null;
        List<String> items = new ArrayList<>();
        itemsNode.forEach(n -> items.add(n.asText()));
        return new GuidePairedSection(items);
    }

    private List<GuidePitfall> parsePitfalls(JsonNode node) {
        List<GuidePitfall> pitfalls = new ArrayList<>();
        if (node == null || !node.isArray()) return pitfalls;
        node.forEach(n -> {
            String text = n.get("text").asText();
            String sourceFieldStr = n.get("sourceField").asText();
            pitfalls.add(new GuidePitfall(text, GuideSourceField.valueOf(sourceFieldStr)));
        });
        return pitfalls;
    }

    private String buildUserMessage(GuideGenerationInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("[정책 메타]\n");
        sb.append("title: ").append(input.title()).append("\n");
        if (input.referenceYear() != null) sb.append("referenceYear: ").append(input.referenceYear()).append("\n");
        if (input.organization() != null) sb.append("organization: ").append(input.organization()).append("\n");
        if (input.contact() != null) sb.append("contact: ").append(input.contact()).append("\n");
        sb.append("\n[원문]\n");
        sb.append(input.combinedSourceText());
        return sb.toString();
    }

    private Map<String, Object> buildResponseFormat() {
        Map<String, Object> pairedSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("items"),
                "properties", Map.of(
                        "items", Map.of("type", "array", "items", Map.of("type", "string"))
                )
        );

        Map<String, Object> pitfallSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("text", "sourceField"),
                "properties", Map.of(
                        "text", Map.of("type", "string"),
                        "sourceField", Map.of(
                                "type", "string",
                                "enum", List.of("SUPPORT_TARGET", "SELECTION_CRITERIA", "SUPPORT_CONTENT", "BODY")
                        )
                )
        );

        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("oneLineSummary", "target", "criteria", "content", "pitfalls"),
                "properties", Map.of(
                        "oneLineSummary", Map.of("type", "string"),
                        "target", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))),
                        "criteria", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))),
                        "content", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))),
                        "pitfalls", Map.of("type", "array", "items", pitfallSchema)
                )
        );

        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "guide_content",
                        "strict", true,
                        "schema", schema
                )
        );
    }
}
