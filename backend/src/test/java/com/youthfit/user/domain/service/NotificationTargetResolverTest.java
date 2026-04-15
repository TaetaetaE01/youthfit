package com.youthfit.user.domain.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationTargetResolver")
class NotificationTargetResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 15);

    @Nested
    @DisplayName("shouldNotify")
    class ShouldNotify {

        @Test
        @DisplayName("마감일이 daysBeforeDeadline 이내이고 OPEN이면 true")
        void withinDeadline_andOpen_returnsTrue() {
            // given
            Policy policy = createOpenPolicy(TODAY.plusDays(3));

            // when
            boolean result = NotificationTargetResolver.shouldNotify(policy, 7, TODAY);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("마감일이 정확히 daysBeforeDeadline일 때 true")
        void exactDeadlineBoundary_returnsTrue() {
            // given
            Policy policy = createOpenPolicy(TODAY.plusDays(7));

            // when
            boolean result = NotificationTargetResolver.shouldNotify(policy, 7, TODAY);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("마감일이 오늘이면 true")
        void deadlineIsToday_returnsTrue() {
            // given
            Policy policy = createOpenPolicy(TODAY);

            // when
            boolean result = NotificationTargetResolver.shouldNotify(policy, 7, TODAY);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("마감일이 daysBeforeDeadline 초과이면 false")
        void beyondDeadline_returnsFalse() {
            // given
            Policy policy = createOpenPolicy(TODAY.plusDays(10));

            // when
            boolean result = NotificationTargetResolver.shouldNotify(policy, 7, TODAY);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("마감일이 이미 지났으면 false")
        void pastDeadline_returnsFalse() {
            // given
            Policy policy = createOpenPolicy(TODAY.minusDays(1));

            // when
            boolean result = NotificationTargetResolver.shouldNotify(policy, 7, TODAY);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("마감일이 null이면 false")
        void nullApplyEnd_returnsFalse() {
            // given
            Policy policy = createOpenPolicy(null);

            // when
            boolean result = NotificationTargetResolver.shouldNotify(policy, 7, TODAY);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("OPEN 상태가 아니면 false")
        void notOpen_returnsFalse() {
            // given
            Policy policy = Policy.builder()
                    .title("테스트 정책")
                    .category(Category.JOBS)
                    .applyEnd(TODAY.plusDays(3))
                    .build();
            // status는 UPCOMING (기본값)

            // when
            boolean result = NotificationTargetResolver.shouldNotify(policy, 7, TODAY);

            // then
            assertThat(result).isFalse();
        }
    }

    // ── 헬퍼 메서드 ──

    private Policy createOpenPolicy(LocalDate applyEnd) {
        Policy policy = Policy.builder()
                .title("테스트 정책")
                .category(Category.JOBS)
                .applyStart(TODAY.minusDays(30))
                .applyEnd(applyEnd)
                .build();
        policy.open();
        return policy;
    }
}
