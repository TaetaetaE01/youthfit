package com.youthfit.qna.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.qna.application.dto.command.PolicyMetadata;
import com.youthfit.qna.application.port.QnaLlmProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
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
            """;

    private final OpenAiQnaProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generateAnswer(String policyTitle, PolicyMetadata metadata, String context, String question, Consumer<String> chunkConsumer) {
        String userMessage = buildUserMessage(policyTitle, metadata, context, question);

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            InputStream inputStream = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(InputStream.class);

            if (inputStream == null) {
                throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "Q&A 답변 생성에 실패했습니다");
            }

            return readStreamResponse(inputStream, chunkConsumer);
        } catch (YouthFitException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI Q&A 스트리밍 호출 실패: policyTitle={}", policyTitle, e);
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "Q&A 답변 생성에 실패했습니다");
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

        return fullAnswer.toString();
    }
}
