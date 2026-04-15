package com.youthfit.guide.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.guide.application.port.GuideLlmProvider;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiChatClient implements GuideLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            당신은 청년 정책 가이드 작성 전문가입니다.
            주어진 정책 원문을 바탕으로 청년이 쉽게 이해할 수 있는 구조화된 가이드를 HTML 형식으로 작성하세요.

            다음 구조를 따르세요:
            1. <h3>한줄 요약</h3> - 정책의 핵심을 한 문장으로
            2. <h3>지원 대상</h3> - 누가 신청할 수 있는지
            3. <h3>지원 내용</h3> - 어떤 혜택을 받는지
            4. <h3>신청 방법</h3> - 어떻게 신청하는지
            5. <h3>준비 서류</h3> - 필요한 서류 목록 (원문에 있는 경우)
            6. <h3>유의 사항</h3> - 주의할 점 (원문에 있는 경우)

            규칙:
            - 원문에 없는 내용을 지어내지 마세요.
            - 원문에 해당 섹션 정보가 없으면 해당 섹션을 생략하세요.
            - 쉬운 한국어로 작성하세요.
            - HTML 태그만 사용하고 마크다운은 사용하지 마세요.
            - 목록은 <ul><li> 태그를 사용하세요.
            """;

    private final OpenAiChatProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public String generateGuideSummary(String policyTitle, String documentContent) {
        String userMessage = "정책명: " + policyTitle + "\n\n정책 원문:\n" + documentContent;

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        JsonNode response = restClient.post()
                .uri(CHAT_COMPLETIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            log.error("OpenAI Chat API 호출 실패: policyTitle={}", policyTitle);
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 생성에 실패했습니다");
        }

        String content = response.get("choices").get(0)
                .get("message").get("content").asText();

        log.info("가이드 생성 완료: policyTitle={}, 응답 길이={}", policyTitle, content.length());
        return content;
    }
}
