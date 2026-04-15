package com.youthfit.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationSetting Entity")
class NotificationSettingTest {

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("기본 생성 시 이메일 알림이 활성화되고 마감 7일 전으로 설정된다")
        void create_defaultValues() {
            // given & when
            NotificationSetting setting = new NotificationSetting(1L);

            // then
            assertThat(setting.getUserId()).isEqualTo(1L);
            assertThat(setting.isEmailEnabled()).isTrue();
            assertThat(setting.getDaysBeforeDeadline()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("updateSetting - 설정 수정")
    class UpdateSetting {

        @Test
        @DisplayName("이메일 비활성화와 알림 일수를 변경한다")
        void update_changesValues() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);

            // when
            setting.updateSetting(false, 3);

            // then
            assertThat(setting.isEmailEnabled()).isFalse();
            assertThat(setting.getDaysBeforeDeadline()).isEqualTo(3);
        }

        @Test
        @DisplayName("동일한 값으로 수정해도 정상 동작한다")
        void update_sameValues_noError() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);

            // when
            setting.updateSetting(true, 7);

            // then
            assertThat(setting.isEmailEnabled()).isTrue();
            assertThat(setting.getDaysBeforeDeadline()).isEqualTo(7);
        }
    }
}
