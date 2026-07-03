package com.youthfit.eval.generate;

import com.youthfit.eval.dataset.EvalQuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAiEvalQuestionClient.parseQuestions")
class OpenAiEvalQuestionClientParseTest {

    @Test
    @DisplayName("JSON 배열 응답을 파싱한다 (코드펜스 허용)")
    void parsesJsonArrayWithCodeFence() {
        String content = """
                ```json
                [
                  {"question": "지원 금액은 얼마인가요?", "questionType": "KEYWORD", "snippet": "월 20만원을 지원"},
                  {"question": "나도 받을 수 있어?", "questionType": "COLLOQUIAL", "snippet": "만 19세~34세 청년"}
                ]
                ```
                """;

        List<GeneratedEvalQuestion> result = OpenAiEvalQuestionClient.parseQuestions(content);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).questionType()).isEqualTo(EvalQuestionType.KEYWORD);
        assertThat(result.get(1).question()).isEqualTo("나도 받을 수 있어?");
    }

    @Test
    @DisplayName("파싱 불가·빈 응답은 빈 리스트")
    void returnsEmptyOnGarbage() {
        assertThat(OpenAiEvalQuestionClient.parseQuestions("응답이 JSON 이 아님")).isEmpty();
        assertThat(OpenAiEvalQuestionClient.parseQuestions(null)).isEmpty();
    }
}
