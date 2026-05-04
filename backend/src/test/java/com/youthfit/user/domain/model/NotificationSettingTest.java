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

    @Nested
    @DisplayName("관심 분야 collection")
    class InterestCollections {

        @Test
        @DisplayName("새 설정의 관심분야는 빈 집합이다")
        void emptyByDefault() {
            NotificationSetting setting = new NotificationSetting(1L);

            assertThat(setting.getInterestCategories()).isEmpty();
            assertThat(setting.getInterestRegions()).isEmpty();
        }

        @Test
        @DisplayName("replaceInterestCategories는 기존 집합을 대체한다")
        void replaceCategories() {
            NotificationSetting setting = new NotificationSetting(1L);

            setting.replaceInterestCategories(java.util.Set.of(
                    com.youthfit.policy.domain.model.Category.HOUSING,
                    com.youthfit.policy.domain.model.Category.JOBS));

            assertThat(setting.getInterestCategories())
                    .containsExactlyInAnyOrder(
                            com.youthfit.policy.domain.model.Category.HOUSING,
                            com.youthfit.policy.domain.model.Category.JOBS);

            setting.replaceInterestCategories(java.util.Set.of(
                    com.youthfit.policy.domain.model.Category.EDUCATION));

            assertThat(setting.getInterestCategories())
                    .containsExactly(com.youthfit.policy.domain.model.Category.EDUCATION);
        }

        @Test
        @DisplayName("replaceInterestRegions는 기존 집합을 대체한다")
        void replaceRegions() {
            NotificationSetting setting = new NotificationSetting(1L);

            setting.replaceInterestRegions(java.util.Set.of(RegionSidoCode.SEOUL, RegionSidoCode.GYEONGGI));

            assertThat(setting.getInterestRegions())
                    .containsExactlyInAnyOrder(RegionSidoCode.SEOUL, RegionSidoCode.GYEONGGI);
        }

        @Test
        @DisplayName("null 입력은 빈 집합으로 처리한다")
        void nullClearsCollections() {
            NotificationSetting setting = new NotificationSetting(1L);

            setting.replaceInterestCategories(java.util.Set.of(com.youthfit.policy.domain.model.Category.HOUSING));
            setting.replaceInterestCategories(null);

            assertThat(setting.getInterestCategories()).isEmpty();
        }
    }

    @Nested
    @DisplayName("canDispatchRecommendation")
    class CanDispatch {

        @Test
        @DisplayName("토글 OFF면 false")
        void toggleOff() {
            NotificationSetting setting = new NotificationSetting(1L);
            setting.replaceInterestCategories(java.util.Set.of(com.youthfit.policy.domain.model.Category.HOUSING));

            EligibilityProfile profile = profileWithLegalDongAndAge();

            assertThat(setting.canDispatchRecommendation(profile)).isFalse();
        }

        @Test
        @DisplayName("관심 분야가 모두 비면 false")
        void interestsEmpty() {
            NotificationSetting setting = new NotificationSetting(1L);
            setting.updateSetting(true, 7, true);

            EligibilityProfile profile = profileWithLegalDongAndAge();

            assertThat(setting.canDispatchRecommendation(profile)).isFalse();
        }

        @Test
        @DisplayName("프로필이 null 이면 false")
        void profileNull() {
            NotificationSetting setting = fullSetting();

            assertThat(setting.canDispatchRecommendation(null)).isFalse();
        }

        @Test
        @DisplayName("프로필 legalDongCode 또는 age 누락 시 false")
        void profileIncomplete() {
            NotificationSetting setting = fullSetting();

            EligibilityProfile noLegal = EligibilityProfile.empty(1L);
            noLegal.changeAge(28);

            EligibilityProfile noAge = EligibilityProfile.empty(1L);
            noAge.changeLegalDongCode("1111000000");

            assertThat(setting.canDispatchRecommendation(noLegal)).isFalse();
            assertThat(setting.canDispatchRecommendation(noAge)).isFalse();
        }

        @Test
        @DisplayName("토글 ON + 관심 분야 1개 이상 + 프로필 필수 채워짐 → true")
        void allConditionsMet() {
            NotificationSetting setting = fullSetting();

            assertThat(setting.canDispatchRecommendation(profileWithLegalDongAndAge())).isTrue();
        }

        private NotificationSetting fullSetting() {
            NotificationSetting setting = new NotificationSetting(1L);
            setting.updateSetting(true, 7, true);
            setting.replaceInterestCategories(java.util.Set.of(com.youthfit.policy.domain.model.Category.HOUSING));
            return setting;
        }

        private EligibilityProfile profileWithLegalDongAndAge() {
            EligibilityProfile profile = EligibilityProfile.empty(1L);
            profile.changeLegalDongCode("1111000000");
            profile.changeAge(28);
            return profile;
        }
    }
}
