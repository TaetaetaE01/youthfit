package com.youthfit.qna.application.service;

import com.youthfit.qna.domain.model.QnaFailedReason;
import com.youthfit.qna.domain.model.QnaHistory;
import com.youthfit.qna.domain.model.QnaHistoryStatus;
import com.youthfit.qna.domain.repository.QnaHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("QnaHistoryWriter")
@ExtendWith(MockitoExtension.class)
class QnaHistoryWriterTest {

    @InjectMocks
    private QnaHistoryWriter writer;

    @Mock
    private QnaHistoryRepository repository;

    @Test
    @DisplayName("startInProgress 는 IN_PROGRESS 상태로 save 후 id 를 반환한다")
    void startInProgress_savesAndReturnsId() {
        QnaHistory saved = QnaHistory.builder().userId(1L).policyId(10L).question("q").build();
        org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 42L);
        given(repository.save(any(QnaHistory.class))).willReturn(saved);

        Long id = writer.startInProgress(1L, 10L, "q");

        assertThat(id).isEqualTo(42L);
    }

    @Test
    @DisplayName("markCompleted 는 IN_PROGRESS history 를 COMPLETED 로 전환 후 save")
    void markCompleted_transitsToCompleted() {
        QnaHistory history = QnaHistory.builder().userId(1L).policyId(10L).question("q").build();
        given(repository.findById(42L)).willReturn(Optional.of(history));

        writer.markCompleted(42L, "답변", "[]");

        assertThat(history.getStatus()).isEqualTo(QnaHistoryStatus.COMPLETED);
        verify(repository).save(history);
    }

    @Test
    @DisplayName("markFailed 는 IN_PROGRESS history 를 FAILED 로 전환 후 save")
    void markFailed_transitsToFailed() {
        QnaHistory history = QnaHistory.builder().userId(1L).policyId(10L).question("q").build();
        given(repository.findById(42L)).willReturn(Optional.of(history));

        writer.markFailed(42L, QnaFailedReason.NO_RELEVANT_CHUNK);

        assertThat(history.getStatus()).isEqualTo(QnaHistoryStatus.FAILED);
        assertThat(history.getFailedReason()).isEqualTo(QnaFailedReason.NO_RELEVANT_CHUNK);
        verify(repository).save(history);
    }

    @Test
    @DisplayName("findById 가 비어 있으면 markCompleted 는 조용히 무시 (이미 정리된 케이스)")
    void markCompleted_missingHistory_silentlyIgnored() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        writer.markCompleted(99L, "답변", "[]");

        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("findById 가 비어 있으면 markFailed 도 조용히 무시 (이미 정리된 케이스)")
    void markFailed_missingHistory_silentlyIgnored() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        writer.markFailed(99L, QnaFailedReason.LLM_ERROR);

        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
