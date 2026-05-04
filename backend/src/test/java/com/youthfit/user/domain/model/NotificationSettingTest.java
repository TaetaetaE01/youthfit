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
        @DisplayName("기본 생성 시 이메일 알림 활성·마감 7일 전·추천 알림 비활성으로 설정된다")
        void create_defaultValues() {
            NotificationSetting setting = new NotificationSetting(1L);

            assertThat(setting.getUserId()).isEqualTo(1L);
            assertThat(setting.isEmailEnabled()).isTrue();
            assertThat(setting.getDaysBeforeDeadline()).isEqualTo(7);
            assertThat(setting.isRecommendationEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("updateSetting - 설정 수정")
    class UpdateSetting {

        @Test
        @DisplayName("이메일·알림 일수·추천 토글을 함께 변경한다")
        void update_changesValues() {
            NotificationSetting setting = new NotificationSetting(1L);

            setting.updateSetting(false, 3, true);

            assertThat(setting.isEmailEnabled()).isFalse();
            assertThat(setting.getDaysBeforeDeadline()).isEqualTo(3);
            assertThat(setting.isRecommendationEnabled()).isTrue();
        }

        @Test
        @DisplayName("동일한 값으로 수정해도 정상 동작한다")
        void update_sameValues_noError() {
            NotificationSetting setting = new NotificationSetting(1L);

            setting.updateSetting(true, 7, false);

            assertThat(setting.isEmailEnabled()).isTrue();
            assertThat(setting.getDaysBeforeDeadline()).isEqualTo(7);
            assertThat(setting.isRecommendationEnabled()).isFalse();
        }

        @Test
        @DisplayName("추천 알림을 켰다가 다시 끄면 false로 돌아간다")
        void update_recommendationEnabledToggle() {
            NotificationSetting setting = new NotificationSetting(1L);

            setting.updateSetting(true, 7, true);
            assertThat(setting.isRecommendationEnabled()).isTrue();

            setting.updateSetting(true, 7, false);
            assertThat(setting.isRecommendationEnabled()).isFalse();
        }
    }
}
