package com.youthfit.qna.infrastructure.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAiQnaClient.parseFollowUps")
class OpenAiQnaClientFollowUpTest {

    @Test
    @DisplayName("정상 JSON 배열을 List<String>으로 파싱한다")
    void parseValidJsonArray() {
        String response = "[\"필요 서류는?\", \"신청 마감일은?\", \"중복 수혜 가능?\"]";

        List<String> result = OpenAiQnaClient.parseFollowUps(response);

        assertThat(result).containsExactly("필요 서류는?", "신청 마감일은?", "중복 수혜 가능?");
    }

    @Test
    @DisplayName("코드펜스로 감싸진 JSON 도 파싱 (LLM 가끔 ```json...``` 출력)")
    void parseJsonWithCodeFence() {
        String response = "```json\n[\"질문1\", \"질문2\"]\n```";

        List<String> result = OpenAiQnaClient.parseFollowUps(response);

        assertThat(result).containsExactly("질문1", "질문2");
    }

    @Test
    @DisplayName("빈 응답이면 빈 리스트")
    void parseEmptyResponse() {
        assertThat(OpenAiQnaClient.parseFollowUps("")).isEmpty();
        assertThat(OpenAiQnaClient.parseFollowUps(null)).isEmpty();
    }

    @Test
    @DisplayName("JSON 이 아닌 응답은 빈 리스트")
    void parseNonJsonResponse() {
        assertThat(OpenAiQnaClient.parseFollowUps("질문이 떠오르지 않습니다.")).isEmpty();
    }

    @Test
    @DisplayName("JSON 배열이지만 string 외 요소가 섞인 경우 string 만 추출")
    void parseMixedArray() {
        String response = "[\"정상 질문\", 123, null, \"또 정상\"]";

        List<String> result = OpenAiQnaClient.parseFollowUps(response);

        assertThat(result).containsExactly("정상 질문", "또 정상");
    }
}
