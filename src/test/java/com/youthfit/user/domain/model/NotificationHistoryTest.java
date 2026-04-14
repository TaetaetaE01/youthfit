package com.youthfit.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationHistory Entity")
class NotificationHistoryTest {

    @Test
    @DisplayName("알림 이력 생성 시 userId, policyId, notificationType이 설정된다")
    void create_setsAllFields() {
        // given & when
        NotificationHistory history = new NotificationHistory(1L, 100L, "DEADLINE");

        // then
        assertThat(history.getUserId()).isEqualTo(1L);
        assertThat(history.getPolicyId()).isEqualTo(100L);
        assertThat(history.getNotificationType()).isEqualTo("DEADLINE");
    }

    @Test
    @DisplayName("생성 직후 id와 sentAt은 null이다")
    void create_idAndSentAtAreNull() {
        // given & when
        NotificationHistory history = new NotificationHistory(1L, 100L, "DEADLINE");

        // then
        assertThat(history.getId()).isNull();
        assertThat(history.getSentAt()).isNull();
    }
}
