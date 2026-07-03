package com.youthfit.eval.generate;

import com.youthfit.eval.config.EvalProperties;
import com.youthfit.eval.dataset.EvalQuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("eval")
public class OpenAiEvalQuestionClient implements EvalQuestionLlm {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEvalQuestionClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper PARSE_MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            당신은 한국 청년 정책 검색 시스템의 평가 데이터를 만드는 어시스턴트입니다.
            주어진 정책 본문 청크들로 "답할 수 있는" 질문을 만드세요.

            규칙:
            1. 질문 3개: KEYWORD 2개(정책명·금액·기관 등 정확 용어 포함), COLLOQUIAL 1개(구어체·짧은 표현).
            2. 각 질문에 snippet 을 붙이세요. snippet 은 반드시 주어진 청크 원문에서
               "한 글자도 바꾸지 않고 그대로" 복사한 1~2문장이어야 합니다. 새로 쓰지 마세요.
            3. 출력은 JSON 배열만 — 다른 텍스트·설명 금지.
               [{"question": "...", "questionType": "KEYWORD", "snippet": "..."}]
            """;

    private final EvalProperties properties;
    private final RestClient restClient;

    public OpenAiEvalQuestionClient(EvalProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public List<GeneratedEvalQuestion> generateQuestions(String policyTitle, List<String> chunkContents) {
        String userMessage = "정책명: " + policyTitle + "\n\n청크:\n" + String.join("\n---\n", chunkContents);
        Map<String, Object> requestBody = Map.of(
                "model", properties.generate().model(),
                "max_tokens", properties.generate().maxTokens(),
                "temperature", 0.5,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            String responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.generate().apiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = PARSE_MAPPER.readTree(responseBody);
            JsonNode choices = root.get("choices");
            String content = (choices != null && !choices.isEmpty())
                    ? choices.get(0).path("message").path("content").asText("")
                    : "";
            return parseQuestions(content);
        } catch (Exception e) {
            log.warn("역질문 생성 실패, 정책 스킵: title=\"{}\", error={}", policyTitle, e.toString());
            return List.of();
        }
    }

    /** 코드펜스를 벗기고 JSON 배열을 파싱한다. 실패 시 빈 리스트. */
    public static List<GeneratedEvalQuestion> parseQuestions(String content) {
        if (content == null || content.isBlank()) return List.of();
        String stripped = content.strip()
                .replaceAll("^```(json)?\\s*", "")
                .replaceAll("\\s*```$", "");
        try {
            JsonNode array = PARSE_MAPPER.readTree(stripped);
            if (!array.isArray()) return List.of();
            List<GeneratedEvalQuestion> out = new ArrayList<>();
            for (JsonNode node : array) {
                String question = node.path("question").asText("");
                String typeName = node.path("questionType").asText("");
                String snippet = node.path("snippet").asText("");
                if (question.isBlank() || snippet.isBlank()) continue;
                EvalQuestionType type;
                try {
                    type = EvalQuestionType.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                out.add(new GeneratedEvalQuestion(question, type, snippet));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
