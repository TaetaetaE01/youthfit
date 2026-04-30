package com.youthfit.qna.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "qna_history")
public class QnaHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String sources;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QnaHistoryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failed_reason", length = 30)
    private QnaFailedReason failedReason;

    @Builder
    private QnaHistory(Long userId, Long policyId, String question) {
        this.userId = userId;
        this.policyId = policyId;
        this.question = question;
        this.status = QnaHistoryStatus.IN_PROGRESS;
    }

    public void markCompleted(String answer, String sources) {
        requireInProgress();
        this.answer = answer;
        this.sources = sources;
        this.status = QnaHistoryStatus.COMPLETED;
    }

    public void markFailed(QnaFailedReason reason) {
        requireInProgress();
        this.failedReason = reason;
        this.status = QnaHistoryStatus.FAILED;
    }

    private void requireInProgress() {
        if (this.status != QnaHistoryStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "QnaHistory 상태 전이 불가: 현재=" + this.status);
        }
    }
}
