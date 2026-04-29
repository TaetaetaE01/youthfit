package com.youthfit.qna.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("QnaHistory")
class QnaHistoryTest {

    @Test
    @DisplayName("새 인스턴스는 IN_PROGRESS 상태로 시작한다")
    void newInstance_startsInProgress() {
        QnaHistory history = QnaHistory.builder()
                .userId(1L)
                .policyId(10L)
                .question("테스트 질문")
                .build();

        assertThat(history.getStatus()).isEqualTo(QnaHistoryStatus.IN_PROGRESS);
        assertThat(history.getFailedReason()).isNull();
        assertThat(history.getAnswer()).isNull();
        assertThat(history.getSources()).isNull();
    }

    @Test
    @DisplayName("markCompleted 는 답변·sources 저장 후 COMPLETED 상태가 된다")
    void markCompleted_setsCompleted() {
        QnaHistory history = QnaHistory.builder()
                .userId(1L).policyId(10L).question("질문").build();

        history.markCompleted("답변", "[{}]");

        assertThat(history.getStatus()).isEqualTo(QnaHistoryStatus.COMPLETED);
        assertThat(history.getAnswer()).isEqualTo("답변");
        assertThat(history.getSources()).isEqualTo("[{}]");
    }

    @Test
    @DisplayName("markFailed 는 사유와 함께 FAILED 상태가 된다")
    void markFailed_setsFailed() {
        QnaHistory history = QnaHistory.builder()
                .userId(1L).policyId(10L).question("질문").build();

        history.markFailed(QnaFailedReason.NO_RELEVANT_CHUNK);

        assertThat(history.getStatus()).isEqualTo(QnaHistoryStatus.FAILED);
        assertThat(history.getFailedReason()).isEqualTo(QnaFailedReason.NO_RELEVANT_CHUNK);
        assertThat(history.getAnswer()).isNull();
    }

    @Test
    @DisplayName("이미 COMPLETED 인 history 에 markCompleted 재호출 시 IllegalStateException")
    void markCompleted_afterCompleted_throws() {
        QnaHistory history = QnaHistory.builder()
                .userId(1L).policyId(10L).question("질문").build();
        history.markCompleted("답변", "[]");

        assertThatThrownBy(() -> history.markCompleted("재답변", "[]"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 FAILED 인 history 에 markFailed 재호출 시 IllegalStateException")
    void markFailed_afterFailed_throws() {
        QnaHistory history = QnaHistory.builder()
                .userId(1L).policyId(10L).question("질문").build();
        history.markFailed(QnaFailedReason.LLM_ERROR);

        assertThatThrownBy(() -> history.markFailed(QnaFailedReason.NO_RELEVANT_CHUNK))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("COMPLETED 후 markFailed 호출도 IllegalStateException")
    void markFailed_afterCompleted_throws() {
        QnaHistory history = QnaHistory.builder()
                .userId(1L).policyId(10L).question("질문").build();
        history.markCompleted("답변", "[]");

        assertThatThrownBy(() -> history.markFailed(QnaFailedReason.LLM_ERROR))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FAILED 후 markCompleted 호출도 IllegalStateException")
    void markCompleted_afterFailed_throws() {
        QnaHistory history = QnaHistory.builder()
                .userId(1L).policyId(10L).question("질문").build();
        history.markFailed(QnaFailedReason.LLM_ERROR);

        assertThatThrownBy(() -> history.markCompleted("답변", "[]"))
                .isInstanceOf(IllegalStateException.class);
    }
}
