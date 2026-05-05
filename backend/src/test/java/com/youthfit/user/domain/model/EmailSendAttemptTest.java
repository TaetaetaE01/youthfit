package com.youthfit.user.domain.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class EmailSendAttemptTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 5, 10, 0);
    private static final String EMAIL = "user@example.com";
    private static final Long USER_ID = 7L;
    private static final Long HISTORY_ID = 100L;

    @Test
    void success_정상_생성() {
        EmailSendAttempt attempt = EmailSendAttempt.success(
            HISTORY_ID, USER_ID, EMAIL, NotificationType.DEADLINE,
            "msg-123", "[YouthFit] 마감 임박", "{\"policyId\":42}", NOW);

        assertThat(attempt.getStatus()).isEqualTo(EmailSendStatus.SENT);
        assertThat(attempt.getSesMessageId()).isEqualTo("msg-123");
        assertThat(attempt.getNotificationHistoryId()).isEqualTo(HISTORY_ID);
        assertThat(attempt.getErrorCode()).isNull();
    }

    @Test
    void failure_정상_생성() {
        EmailSendAttempt attempt = EmailSendAttempt.failure(
            HISTORY_ID, USER_ID, EMAIL, NotificationType.DEADLINE,
            "[YouthFit] 마감 임박", "{\"policyId\":42}",
            "SES_THROTTLE", "Throttling: Maximum sending rate exceeded", NOW);

        assertThat(attempt.getStatus()).isEqualTo(EmailSendStatus.FAILED);
        assertThat(attempt.getSesMessageId()).isNull();
        assertThat(attempt.getErrorCode()).isEqualTo("SES_THROTTLE");
    }

    @Test
    void markDelivered_SENT에서만_허용() {
        EmailSendAttempt attempt = sentAttempt();
        attempt.markDelivered(NOW.plusSeconds(5));
        assertThat(attempt.getStatus()).isEqualTo(EmailSendStatus.DELIVERED);
    }

    @Test
    void markDelivered_DELIVERED에서_재호출시_예외() {
        EmailSendAttempt attempt = sentAttempt();
        attempt.markDelivered(NOW.plusSeconds(5));
        assertThatThrownBy(() -> attempt.markDelivered(NOW.plusSeconds(10)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markBounced_SENT에서만_허용_사유저장() {
        EmailSendAttempt attempt = sentAttempt();
        attempt.markBounced(NOW.plusSeconds(30), "Permanent",
                            "5.1.1 The email account does not exist");
        assertThat(attempt.getStatus()).isEqualTo(EmailSendStatus.BOUNCED);
        assertThat(attempt.getBounceType()).isEqualTo("Permanent");
        assertThat(attempt.getErrorMessage()).contains("5.1.1");
    }

    @Test
    void markComplained_SENT_또는_DELIVERED에서_허용() {
        EmailSendAttempt sent = sentAttempt();
        sent.markComplained(NOW.plusDays(2));
        assertThat(sent.getStatus()).isEqualTo(EmailSendStatus.COMPLAINED);

        EmailSendAttempt delivered = sentAttempt();
        delivered.markDelivered(NOW.plusSeconds(5));
        delivered.markComplained(NOW.plusDays(2));
        assertThat(delivered.getStatus()).isEqualTo(EmailSendStatus.COMPLAINED);
    }

    @Test
    void markComplained_FAILED에서_예외() {
        EmailSendAttempt failed = EmailSendAttempt.failure(
            HISTORY_ID, USER_ID, EMAIL, NotificationType.DEADLINE,
            "subj", "{}", "SES_X", "msg", NOW);
        assertThatThrownBy(() -> failed.markComplained(NOW.plusDays(1)))
            .isInstanceOf(IllegalStateException.class);
    }

    private EmailSendAttempt sentAttempt() {
        return EmailSendAttempt.success(HISTORY_ID, USER_ID, EMAIL,
            NotificationType.DEADLINE, "msg-123", "subj", "{}", NOW);
    }
}
