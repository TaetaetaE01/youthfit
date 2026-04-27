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
            너는 한국 청년 정책 가이드 작성자다. 사용자는 정책 용어가 처음인 일반 청년이다.
            가장 중요한 임무는 행정·법률 용어를 일상어로 적극 풀어 다시 쓰는 것이다.
            원문을 그대로 가져다 붙이는 것은 실패다 — 사용자가 한 번 읽고 자기 상황에 즉시 대입할 수 있어야 한다.

            원칙:
            1. 입력 원문에만 근거하라. 원문에 없는 조건/금액/기한/자격은 절대 추가하지 마라.
            2. 원문에 명시된 모든 금액·연령·기간·지역·비율 수치는 풀이에 누락 없이 보존하라.
            3. 행정 용어를 발견하면 반드시 일상어로 풀어 다시 써라. 절대 원문 그대로 두지 마라. 다음 매핑을 참고:
               - "무주택세대구성원" → "본인 명의 집이 없고, 가구 안에서 같이 사는 가족"
               - "중위소득 60% 이하" → "전국 가구 소득을 줄 세웠을 때 중간 가구 소득의 60% 이하 (정확한 금액은 매년 정부 발표)"
               - "전년도 도시근로자 가구당 월평균소득 100% 이하" → "작년 도시 직장인 가구가 한 달에 평균 번 소득 이하"
               - "임대의무기간" → "법으로 정해진 임대 기간"
               - "입주자모집공고일" → "정부가 입주자를 모집한다고 공식 발표한 날"
            4. 문장은 짧고 능동적으로. 원문의 긴 한 문장을 여러 짧은 불릿로 쪼개라. 한 불릿은 한 가지 조건만.
            5. 어조는 단정형(예: "~다", "~함", 명사형). 친근체("~예요", "~드려요", "~해요")는 절대 금지.
            6. 추정/가정/예시("~일 수 있다", "~로 추정") 금지. 환경값(중위소득 환산 금액 등) 정확한 숫자는 입력에 있을 때만 사용.
            7. 원문이 이미 짧고 쉬워 보여도 그대로 두지 말고 사용자가 자기 상황에 바로 대입할 수 있는 형태로 한 번 더 풀어 다시 써라.

            출력 단위:
            - oneLineSummary: 정책의 정체를 누가/무엇을/얼마나 받는지 1~2문장으로.
            - target: supportTarget 원문의 풀이 (일상어 불릿 배열). 입력에 supportTarget이 비어있으면 null.
            - criteria: selectionCriteria 원문의 풀이. 입력에 selectionCriteria가 비어있으면 null.
            - content: supportContent 원문의 풀이. 입력에 supportContent가 비어있으면 null.
            - pitfalls: 사용자가 원문을 한 번 읽고 놓칠 만한 함정 항목. 각 항목에 sourceField (SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY) 라벨 필수.

            [변환 예시 1]
            supportTarget 원문:
            "입주자모집공고일 현재 해당 주택건설지역에 거주하는 무주택세대구성원으로서 해당 세대의 소득 및 보유자산이 국토교통부장관이 정하는 기준 이하인 자"
            → target.items:
            - "정부가 공식 발표한 입주자 모집일 기준으로 해당 주택이 지어지는 지역에 살고 있을 것"
            - "본인 명의 집이 없고 가구 안에서 같이 사는 가족일 것"
            - "가구의 소득과 자산(땅·건물·자동차)이 국토교통부가 정한 기준 이하일 것"

            [변환 예시 2]
            selectionCriteria 원문:
            "전용면적 60㎡ 이하 공공분양 및 분양전환공공임대주택 : 전년도 도시근로자 가구당 월평균소득 100% 이하"
            → criteria.items:
            - "60㎡ 이하 공공분양·분양전환공공임대 주택의 경우"
            - "작년 도시 직장인 가구가 한 달에 평균 번 소득 이하일 때 신청 가능"
            - "정확한 금액은 매년 입주자모집공고문에 공개됨"

            [변환 예시 3]
            supportContent 원문:
            "(분양전환공공임대(5년,10년)) 임대의무기간 임대 후 분양전환되는 공공임대주택 공급"
            → content.items:
            - "5년 또는 10년 임대 기간 동안 먼저 임대로 거주"
            - "임대 기간이 끝나면 그 집을 분양받아 본인 소유로 전환 가능"
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
