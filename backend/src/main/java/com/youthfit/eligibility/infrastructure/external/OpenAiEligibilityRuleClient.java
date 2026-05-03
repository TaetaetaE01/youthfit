package com.youthfit.eligibility.infrastructure.external;

import com.youthfit.eligibility.application.dto.command.EligibilityRuleExtractionInput;
import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.application.port.EligibilityRuleLlmProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
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
public class OpenAiEligibilityRuleClient implements EligibilityRuleLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEligibilityRuleClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            너는 한국 청년 정책의 자격 요건을 구조화된 JSON 룰로 추출하는 전문가다.

            [작성 원칙]
            1. 정보 통제: 입력된 원문에만 근거. 원문에 없는 조건/숫자/지역/고용/학력 추가 금지.
            2. 출력 형식: { "rules": [...] } JSON 객체. rules 외 키 금지.
            3. 필드 화이트리스트 (이 10개 외 추출 금지):
               age, region, incomeMin, incomeMax, annualIncome,
               maritalStatus, employmentKind, education, majorField, specializationField
            4. 오퍼레이터 화이트리스트: EQ, NOT_EQ, GTE, LTE, IN, BETWEEN
            5. 값 형식 규칙:
               - age + BETWEEN: "19~34" / age + GTE·LTE: 정수
               - region + EQ: 시도 코드 (SEOUL, BUSAN, DAEGU, INCHEON, GWANGJU, DAEJEON, ULSAN,
                 SEJONG, GYEONGGI, GANGWON, CHUNGBUK, CHUNGNAM, JEONBUK, JEONNAM, GYEONGBUK, GYEONGNAM, JEJU)
               - incomeMin/Max/annualIncome: 연소득 원 단위 정수 (예: "32000000")
               - maritalStatus + EQ/NOT_EQ: SINGLE | MARRIED
               - employmentKind: EMPLOYEE | SELF_EMPLOYED | UNEMPLOYED | FREELANCER
                                | DAILY_WORKER | ENTREPRENEUR | PART_TIME | FARMER | OTHER
               - education: UNDER_HIGH | HIGH_SCHOOL_IN | HIGH_SCHOOL_EXPECTED
                            | HIGH_SCHOOL_GRAD | COLLEGE_IN | COLLEGE_EXPECTED
                            | COLLEGE_GRAD | GRADUATE | OTHER
               - majorField: HUMANITIES | SOCIAL | ECONOMICS | NATURAL | ENGINEERING
                             | ARTS | AGRICULTURE | OTHER
               - specializationField: SME | WOMAN | BASIC_LIVELIHOOD | SINGLE_PARENT
                             | DISABLED | FARMER | MILITARY | LOCAL_TALENT | OTHER
            6. 의미 매핑:
               - "청년" → age BETWEEN 19~34 (정책에 다른 연령 명시되면 그 값 우선)
               - "재직자/근로 청년" → employmentKind EMPLOYEE 또는 IN 으로 추론
            7. confidence 등급:
               - HIGH: 원문에 정확한 수치/단어 명시
               - MEDIUM: 합리적 추론
               - LOW: 모호하거나 추론 폭이 큼
            8. 추출 안 함 (스킵):
               - 가구 형태 / 무주택 / 세대주 / 자가 보유 — 평가기 미지원
               - 부양가족 수 / 가구원 수 — 평가기 미지원
               - 신용등급 / 연체 이력 — 평가기 미지원
               화이트리스트 외 모든 자격 조건은 룰로 만들지 않는다.
            9. label: 한국어 표시명 1~10자 (예: "연령", "거주지", "연소득", "고용 형태").
            10. sourceReference: 원문에서 인용한 자격 조건 구절 1줄 (50자 내외).
            11. 룰이 추출되지 않으면 빈 배열 반환. 빈 룰을 만들어내지 않는다.
            12. 어조: 명사형/단정형. 친근체 금지.
            """;

    private final OpenAiEligibilityRuleProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RawExtractedRule> extractRules(EligibilityRuleExtractionInput input) {
        return callOpenAi(SYSTEM_PROMPT, input.combinedSourceText());
    }

    @Override
    public List<RawExtractedRule> regenerateWithFeedback(
            EligibilityRuleExtractionInput input, List<String> feedbackMessages) {
        StringBuilder retryUserPrompt = new StringBuilder();
        retryUserPrompt.append(input.combinedSourceText());
        retryUserPrompt.append("\n\n[이전 응답 검증 위반]\n");
        for (String msg : feedbackMessages) {
            retryUserPrompt.append("- ").append(msg).append("\n");
        }
        retryUserPrompt.append("\n위 위반을 모두 해결한 새 응답을 동일 JSON 스키마로 출력하라.");
        return callOpenAi(SYSTEM_PROMPT, retryUserPrompt.toString());
    }

    private List<RawExtractedRule> callOpenAi(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "temperature", 0,
                "max_tokens", properties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 호출 실패: " + e.getMessage(), e);
        }

        if (responseBody == null) {
            throw new IllegalStateException("OpenAI 응답 본문 없음");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode rules = parsed.path("rules");
            if (!rules.isArray()) {
                log.warn("OpenAI 응답에 rules 배열 없음: {}", content);
                return List.of();
            }
            List<RawExtractedRule> result = new ArrayList<>();
            for (JsonNode r : rules) {
                result.add(new RawExtractedRule(
                        r.path("field").asText(null),
                        r.path("operator").asText(null),
                        r.path("value").asText(null),
                        r.path("label").asText(null),
                        r.path("sourceReference").asText(null),
                        r.path("confidence").asText(null)
                ));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    @Configuration
    @EnableConfigurationProperties(OpenAiEligibilityRuleProperties.class)
    static class Config {
    }
}
