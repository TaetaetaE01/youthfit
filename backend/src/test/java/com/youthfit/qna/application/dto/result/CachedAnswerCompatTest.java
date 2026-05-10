package com.youthfit.qna.application.dto.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CachedAnswer 직렬화 호환성")
class CachedAnswerCompatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("followUpQuestions 키가 없는 기존 JSON 도 역직렬화 가능 (빈 리스트로)")
    void deserialize_legacy_entry_without_followUps() throws Exception {
        String legacyJson = """
                {
                    "answer": "신청 자격은 만 19~34세입니다.",
                    "sources": [],
                    "cachedAt": "2026-05-01T12:00:00Z"
                }
                """;

        CachedAnswer answer = objectMapper.readValue(legacyJson, CachedAnswer.class);

        assertThat(answer.answer()).isEqualTo("신청 자격은 만 19~34세입니다.");
        assertThat(answer.followUpQuestions()).isEmpty();
    }

    @Test
    @DisplayName("followUpQuestions 가 포함된 신규 JSON 도 정상 역직렬화")
    void deserialize_new_entry_with_followUps() throws Exception {
        String newJson = """
                {
                    "answer": "신청 자격은 만 19~34세입니다.",
                    "sources": [],
                    "followUpQuestions": ["서류는?", "마감일은?"],
                    "cachedAt": "2026-05-01T12:00:00Z"
                }
                """;

        CachedAnswer answer = objectMapper.readValue(newJson, CachedAnswer.class);

        assertThat(answer.followUpQuestions()).containsExactly("서류는?", "마감일은?");
    }
}
