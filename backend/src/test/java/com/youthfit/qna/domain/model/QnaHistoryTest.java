package com.youthfit.qna.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QnaHistory Entity")
class QnaHistoryTest {

    @Test
    @DisplayName("Q&A 생성 시 answer와 sources는 null이다")
    void create_answerAndSourcesAreNull() {
        // given & when
        QnaHistory history = QnaHistory.builder()
                .userId(1L)
                .policyId(10L)
                .question("이 정책의 지원 자격은?")
                .build();

        // then
        assertThat(history.getQuestion()).isEqualTo("이 정책의 지원 자격은?");
        assertThat(history.getAnswer()).isNull();
        assertThat(history.getSources()).isNull();
    }

    @Test
    @DisplayName("답변을 완성하면 answer와 sources가 채워진다")
    void completeAnswer_setsAnswerAndSources() {
        // given
        QnaHistory history = QnaHistory.builder()
                .userId(1L)
                .policyId(10L)
                .question("이 정책의 지원 자격은?")
                .build();

        // when
        history.completeAnswer(
                "만 19~34세 청년이 대상입니다.",
                "[{\"url\":\"https://example.com\"}]");

        // then
        assertThat(history.getAnswer()).isEqualTo("만 19~34세 청년이 대상입니다.");
        assertThat(history.getSources()).contains("https://example.com");
    }
}
