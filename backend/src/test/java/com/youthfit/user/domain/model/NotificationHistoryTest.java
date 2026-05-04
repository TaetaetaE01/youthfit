package com.youthfit.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NotificationHistory Entity")
class NotificationHistoryTest {

    @Test
    @DisplayName("pending 호출 시 userId, policyId, notificationType이 설정된다")
    void pending_setsAllFields() {
        // given & when
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(history.getUserId()).isEqualTo(1L);
        assertThat(history.getPolicyId()).isEqualTo(100L);
        assertThat(history.getNotificationType()).isEqualTo(NotificationType.DEADLINE);
    }

    @Test
    @DisplayName("pending 직후 id와 sentAt은 null이다")
    void pending_idAndSentAtAreNull() {
        // given & when
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(history.getId()).isNull();
        assertThat(history.getSentAt()).isNull();
    }

    @Test
    @DisplayName("pending 정적 팩토리는 status=PENDING, createdAt=now 으로 생성한다")
    void pending_setsStatusAndCreatedAt() {
        // given & when
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(history.getCreatedAt()).isNotNull();
        assertThat(history.getSentAt()).isNull();
        assertThat(history.getFailedAt()).isNull();
        assertThat(history.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("markSent 는 PENDING → SENT 로 전이하고 sentAt 을 채운다")
    void markSent_pendingToSent() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        LocalDateTime now = LocalDateTime.now();

        // when
        history.markSent(now);

        // then
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(history.getSentAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("markFailed 는 PENDING → FAILED 로 전이하고 failedAt/failureReason 을 채운다")
    void markFailed_pendingToFailed() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        LocalDateTime now = LocalDateTime.now();

        // when
        history.markFailed(now, "SES 발송 실패: foo@example.com");

        // then
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(history.getFailedAt()).isEqualTo(now);
        assertThat(history.getFailureReason()).isEqualTo("SES 발송 실패: foo@example.com");
    }

    @Test
    @DisplayName("SENT 상태에서 markSent 재호출은 IllegalStateException")
    void markSent_fromSent_throws() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        history.markSent(LocalDateTime.now());

        // when & then
        assertThatThrownBy(() -> history.markSent(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FAILED 상태에서 markFailed 재호출은 IllegalStateException")
    void markFailed_fromFailed_throws() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        history.markFailed(LocalDateTime.now(), "1차 실패");

        // when & then
        assertThatThrownBy(() -> history.markFailed(LocalDateTime.now(), "2차 실패"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markFailed 의 reason 이 500자 초과면 자른다")
    void markFailed_truncatesReason() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        String longReason = "x".repeat(600);

        // when
        history.markFailed(LocalDateTime.now(), longReason);

        // then
        assertThat(history.getFailureReason()).hasSize(500);
    }
}
