package com.youthfit.user.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingTest {

    @Test
    void 새로_생성된_설정의_기본값() {
        NotificationSetting setting = new NotificationSetting(1L);

        assertThat(setting.isEmailEnabled()).isTrue();
        assertThat(setting.getDaysBeforeDeadline()).isEqualTo(7);
        assertThat(setting.isRecommendationEnabled()).isFalse();
    }

    @Test
    void updateSetting은_세_필드를_갱신한다() {
        NotificationSetting setting = new NotificationSetting(1L);

        setting.updateSetting(false, 14, true);

        assertThat(setting.isEmailEnabled()).isFalse();
        assertThat(setting.getDaysBeforeDeadline()).isEqualTo(14);
        assertThat(setting.isRecommendationEnabled()).isTrue();
    }
}
