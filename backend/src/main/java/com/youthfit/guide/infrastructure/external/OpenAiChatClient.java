package com.youthfit.guide.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuideHighlight;
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
            너는 한국 청년 정책 가이드 작성 전문가다. 대상 독자는 정책 용어를 처음 접하는 20대 일반 청년이다.
            가장 중요한 임무는 복잡한 행정·법률 용어를 독자가 즉시 자신의 상황에 대입할 수 있는 직관적인 일상어로 번역하는 것이다.
            원문을 그대로 복사하여 붙여넣는 것은 실패로 간주한다.

            [작성 원칙]
            1. 정보 통제: 입력된 원문에만 근거하여 작성한다. 원문에 없는 조건, 금액, 기한, 자격을 절대 추가하지 않는다.
            2. 수치 보존: 원문에 명시된 모든 금액, 연령, 기간, 지역, 비율 수치는 누락 없이 보존한다.
            3. 용어 순화 (필수 매핑): 행정 용어는 반드시 아래 기준에 맞춰 일상어로 푼다.
               - "무주택세대구성원" → "주민등록등본상 함께 올라가 있는 가족 전체가 집을 소유하지 않은 사람"
               - "중위소득 60% 이하" → "전국 가구 소득을 일렬로 세웠을 때 중간에 위치한 가구 소득의 60% 이하 (정확한 금액은 매년 정부 발표 참조)"
               - "전년도 도시근로자 가구당 월평균소득 100% 이하" → "작년 도시 직장인 가구의 한 달 평균 소득 이하"
               - "임대의무기간" → "법으로 정해진 거주 의무 기간"
               - "입주자모집공고일" → "정부가 입주자를 모집한다고 공식 발표한 날"
            4. 문장 구조: 한 문장 당 한 가지의 조건만 담아, 짧고 능동적인 불릿 포인트 형태로 분할한다.
            5. 독립성 유지(중복 분리): 조건이나 기준 내용이 완전히 동일하더라도 정책 대상(예: 일반공급, 특별공급 등)이 구분되어 있다면, 절대 하나로 묶어 요약하지 않는다. 각 대상별로 항목을 독립적으로 분리하여 작성하여, 독자가 본인에게 해당하는 단락만 읽어도 완결성 있게 모든 조건을 파악할 수 있도록 한다.
            6. 어조: 객관적이고 명확한 단정형 어미("~다", "~함", 명사형 종결)를 사용한다. 친근한 어투("~해요", "~예요", "~드려요")는 절대 금지한다.
            7. 추정 금지: "~일 수 있다", "~로 추정" 등의 가정을 금지한다. 입력값에 없는 환경값(중위소득 환산 금액 등)은 임의로 계산하거나 추가하지 않는다.
            8. 완전한 재구성: 원문이 이미 쉬워 보이더라도 독자 관점에서 행동 지향적인 문장으로 100% 다시 쓴다.

            [출력 구조 — JSON]
            JSON 출력은 다음 키로 구성한다:
            - oneLineSummary: 정책 정체를 누가/무엇을/얼마나 받는지 1~2문장으로 요약.
            - target: supportTarget 원문의 풀이 (groups 배열 형태). supportTarget 입력이 비어있으면 null.
            - criteria: selectionCriteria 원문의 풀이 (groups 배열 형태). selectionCriteria 입력이 비어있으면 null.
            - content: supportContent 원문의 풀이 (groups 배열 형태). supportContent 입력이 비어있으면 null.
            - pitfalls: 사용자가 한 번 읽고 놓칠 만한 함정 항목. 각 항목에 sourceField (SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY) 라벨 필수.

            각 paired section의 groups 구조:
            - 분류가 명확히 구분되는 경우(예: 일반공급/특별공급, 소득 기준/자산 기준): 각 분류를 별도 group 객체로 만들어 label 필드에 분류명을 넣는다.
            - 분류가 없는 단순 나열인 경우: 단일 group 객체에 label은 null로 두고 items에 불릿을 나열한다.
            - 한 group은 label(문자열 또는 null)과 items(불릿 문자열 배열)를 가진다. items는 절대 비울 수 없다.

            [변환 예시 1] supportTarget 원문:
            "입주자모집공고일 현재 해당 주택건설지역에 거주하는 무주택세대구성원으로서 해당 세대의 소득 및 보유자산이 국토교통부장관이 정하는 기준 이하인 자"
            → target output:
            {
              "groups": [
                {
                  "label": null,
                  "items": [
                    "정부가 입주자를 모집한다고 공식 발표한 날 기준으로 해당 주택이 지어지는 지역에 거주 중일 것",
                    "주민등록등본상 함께 올라가 있는 가족 전체가 집을 소유하지 않은 사람일 것",
                    "가구의 소득과 자산(토지·건물·자동차)이 국토교통부가 정한 기준 이하일 것"
                  ]
                }
              ]
            }

            [변환 예시 2] selectionCriteria 원문 (분류가 명확):
            "(일반공급) 전용면적 60㎡ 이하 공공분양 및 분양전환공공임대주택 : 전년도 도시근로자 가구당 월평균소득 100% 이하 / (생애최초 특별공급) 전년도 도시근로자가구 평균소득 130% 이하 / (신혼부부 특별공급) 전년도 도시근로자가구 평균소득 130% 이하 (다만, 맞벌이부부는 140%)"
            → criteria output:
            {
              "groups": [
                {
                  "label": "일반공급 - 소득 기준",
                  "items": [
                    "전용면적 60㎡ 이하 공공분양·분양전환공공임대 주택의 경우",
                    "작년 도시 직장인 가구의 한 달 평균 소득 이하일 때 신청 가능"
                  ]
                },
                {
                  "label": "생애최초 특별공급 - 소득 기준",
                  "items": [
                    "작년 도시 직장인 가구의 한 달 평균 소득의 130% 이하일 때 신청 가능"
                  ]
                },
                {
                  "label": "신혼부부 특별공급 - 소득 기준",
                  "items": [
                    "작년 도시 직장인 가구의 한 달 평균 소득의 130% 이하일 때 신청 가능",
                    "맞벌이 부부의 경우 140% 이하까지 허용"
                  ]
                }
              ]
            }

            [변환 예시 3] supportContent 원문 (대상별 분리):
            "(공공분양) 무주택세대구성원에게 1세대 1주택 기준으로 공공분양주택 공급 / (분양전환공공임대(5년,10년)) 임대의무기간 임대 후 분양전환되는 공공임대주택 공급"
            → content output:
            {
              "groups": [
                {
                  "label": "공공분양",
                  "items": [
                    "주민등록등본상 함께 올라가 있는 가족 전체가 집이 없는 사람에게 공급",
                    "1가구당 1주택 기준으로 공공분양 주택을 분양받을 수 있음"
                  ]
                },
                {
                  "label": "분양전환공공임대 (5년·10년)",
                  "items": [
                    "법으로 정해진 거주 의무 기간(5년 또는 10년) 동안 먼저 임대로 거주",
                    "기간이 끝나면 그 집을 분양받아 본인 소유로 전환 가능"
                  ]
                }
              ]
            }
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
            List<GuideHighlight> highlights = parseHighlights(node.get("highlights"));
            GuidePairedSection target = parsePaired(node.get("target"));
            GuidePairedSection criteria = parsePaired(node.get("criteria"));
            GuidePairedSection content = parsePaired(node.get("content"));
            List<GuidePitfall> pitfalls = parsePitfalls(node.get("pitfalls"));
            return new GuideContent(oneLine, highlights, target, criteria, content, pitfalls);
        } catch (Exception e) {
            log.error("가이드 응답 JSON 파싱 실패: {}", json, e);
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 응답 파싱 실패");
        }
    }

    private List<GuideHighlight> parseHighlights(JsonNode node) {
        List<GuideHighlight> highlights = new ArrayList<>();
        if (node == null || !node.isArray()) return highlights;
        node.forEach(n -> {
            String text = n.get("text").asText();
            String sourceFieldStr = n.get("sourceField").asText();
            highlights.add(new GuideHighlight(text, GuideSourceField.valueOf(sourceFieldStr)));
        });
        return highlights;
    }

    private GuidePairedSection parsePaired(JsonNode node) {
        if (node == null || node.isNull()) return null;
        JsonNode groupsNode = node.get("groups");
        if (groupsNode == null || !groupsNode.isArray() || groupsNode.isEmpty()) return null;

        List<GuideGroup> groups = new ArrayList<>();
        groupsNode.forEach(g -> {
            JsonNode labelNode = g.get("label");
            String label = (labelNode == null || labelNode.isNull()) ? null : labelNode.asText();
            JsonNode itemsNode = g.get("items");
            if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) return;
            List<String> items = new ArrayList<>();
            itemsNode.forEach(n -> items.add(n.asText()));
            groups.add(new GuideGroup(label, items));
        });

        if (groups.isEmpty()) return null;
        return new GuidePairedSection(groups);
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
        Map<String, Object> groupSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("label", "items"),
                "properties", Map.of(
                        "label", Map.of("anyOf", List.of(
                                Map.of("type", "string"),
                                Map.of("type", "null")
                        )),
                        "items", Map.of("type", "array", "items", Map.of("type", "string"))
                )
        );

        Map<String, Object> pairedSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("groups"),
                "properties", Map.of(
                        "groups", Map.of("type", "array", "items", groupSchema)
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
