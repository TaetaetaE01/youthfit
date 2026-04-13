package com.youthfit.policy.domain.model;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Policy Entity")
class PolicyTest {

    @Test
    @DisplayName("정책 생성 시 기본 상태는 UPCOMING이고 detailLevel은 LITE이다")
    void create_defaultValues() {
        // given & when
        Policy policy = createUpcomingPolicy();

        // then
        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.UPCOMING);
        assertThat(policy.getDetailLevel()).isEqualTo(DetailLevel.LITE);
    }

    @Nested
    @DisplayName("open - 모집 시작")
    class Open {

        @Test
        @DisplayName("UPCOMING 상태에서 OPEN으로 전이한다")
        void fromUpcoming_statusChangesToOpen() {
            // given
            Policy policy = createUpcomingPolicy();

            // when
            policy.open();

            // then
            assertThat(policy.getStatus()).isEqualTo(PolicyStatus.OPEN);
        }

        @Test
        @DisplayName("OPEN 상태에서 호출하면 INVALID_INPUT 예외가 발생한다")
        void fromOpen_throwsException() {
            // given
            Policy policy = createUpcomingPolicy();
            policy.open();

            // when & then
            assertThatThrownBy(() -> policy.open())
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    });
        }

        @Test
        @DisplayName("CLOSED 상태에서 호출하면 INVALID_INPUT 예외가 발생한다")
        void fromClosed_throwsException() {
            // given
            Policy policy = createUpcomingPolicy();
            policy.open();
            policy.close();

            // when & then
            assertThatThrownBy(() -> policy.open())
                    .isInstanceOf(YouthFitException.class);
        }
    }

    @Nested
    @DisplayName("close - 모집 마감")
    class Close {

        @Test
        @DisplayName("OPEN 상태에서 CLOSED로 전이한다")
        void fromOpen_statusChangesToClosed() {
            // given
            Policy policy = createUpcomingPolicy();
            policy.open();

            // when
            policy.close();

            // then
            assertThat(policy.getStatus()).isEqualTo(PolicyStatus.CLOSED);
        }

        @Test
        @DisplayName("UPCOMING 상태에서 호출하면 INVALID_INPUT 예외가 발생한다")
        void fromUpcoming_throwsException() {
            // given
            Policy policy = createUpcomingPolicy();

            // when & then
            assertThatThrownBy(() -> policy.close())
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    });
        }
    }

    @Nested
    @DisplayName("upgradeDetailLevel - 상세 수준 업그레이드")
    class UpgradeDetailLevel {

        @Test
        @DisplayName("더 높은 레벨로 업그레이드하면 변경된다")
        void higherLevel_upgrades() {
            // given
            Policy policy = createUpcomingPolicy(); // LITE

            // when
            policy.upgradeDetailLevel(DetailLevel.FULL);

            // then
            assertThat(policy.getDetailLevel()).isEqualTo(DetailLevel.FULL);
        }

        @Test
        @DisplayName("같거나 낮은 레벨로는 변경되지 않는다")
        void sameOrLowerLevel_noChange() {
            // given
            Policy policy = createUpcomingPolicy();
            policy.upgradeDetailLevel(DetailLevel.FULL);

            // when
            policy.upgradeDetailLevel(DetailLevel.LITE);

            // then
            assertThat(policy.getDetailLevel()).isEqualTo(DetailLevel.FULL);
        }
    }

    @Nested
    @DisplayName("isOpen / isExpired - 상태 조회")
    class StatusQuery {

        @Test
        @DisplayName("OPEN 상태이면 isOpen은 true를 반환한다")
        void openStatus_isOpenReturnsTrue() {
            // given
            Policy policy = createUpcomingPolicy();
            policy.open();

            // then
            assertThat(policy.isOpen()).isTrue();
        }

        @Test
        @DisplayName("UPCOMING 상태이면 isOpen은 false를 반환한다")
        void upcomingStatus_isOpenReturnsFalse() {
            // given
            Policy policy = createUpcomingPolicy();

            // then
            assertThat(policy.isOpen()).isFalse();
        }

        @Test
        @DisplayName("마감일이 오늘 이전이면 만료 상태이다")
        void pastEndDate_isExpiredReturnsTrue() {
            // given
            Policy policy = Policy.builder()
                    .title("만료 정책")
                    .category(Category.JOBS)
                    .applyEnd(LocalDate.now().minusDays(1))
                    .build();

            // then
            assertThat(policy.isExpired()).isTrue();
        }

        @Test
        @DisplayName("마감일이 오늘 이후이면 만료되지 않은 상태이다")
        void futureEndDate_isExpiredReturnsFalse() {
            // given
            Policy policy = Policy.builder()
                    .title("진행 중 정책")
                    .category(Category.JOBS)
                    .applyEnd(LocalDate.now().plusDays(30))
                    .build();

            // then
            assertThat(policy.isExpired()).isFalse();
        }

        @Test
        @DisplayName("마감일이 null이면 만료되지 않은 상태이다")
        void nullEndDate_isExpiredReturnsFalse() {
            // given
            Policy policy = Policy.builder()
                    .title("상시 모집")
                    .category(Category.JOBS)
                    .build();

            // then
            assertThat(policy.isExpired()).isFalse();
        }
    }

    // ── 헬퍼 메서드 ──

    private Policy createUpcomingPolicy() {
        return Policy.builder()
                .title("청년 주거 지원")
                .summary("월세 지원 프로그램")
                .category(Category.HOUSING)
                .regionCode("11")
                .applyStart(LocalDate.of(2026, 1, 1))
                .applyEnd(LocalDate.of(2026, 12, 31))
                .build();
    }
}
