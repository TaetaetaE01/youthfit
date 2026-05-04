package com.youthfit.user.domain.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.RegionSidoCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyRecommender")
class PolicyRecommenderTest {

    private final PolicyRecommender recommender = new PolicyRecommender();

    @Nested
    @DisplayName("filterByInterest")
    class FilterByInterest {

        @Test
        @DisplayName("카테고리와 지역이 모두 매칭되는 정책만 추출한다")
        void 카테고리와_지역_매칭_정책만_추출() {
            // given
            NotificationSetting setting = createSetting(
                    Set.of(Category.JOBS),
                    Set.of(RegionSidoCode.SEOUL)
            );
            Policy match = createPolicy(1L, Category.JOBS, "11", LocalDate.now().plusDays(5), LocalDateTime.now());
            Policy wrongCategory = createPolicy(2L, Category.HOUSING, "11", LocalDate.now().plusDays(5), LocalDateTime.now());
            Policy wrongRegion = createPolicy(3L, Category.JOBS, "26", LocalDate.now().plusDays(5), LocalDateTime.now());

            // when
            List<Policy> result = recommender.filterByInterest(setting, List.of(match, wrongCategory, wrongRegion));

            // then
            assertThat(result).containsExactly(match);
        }

        @Test
        @DisplayName("카테고리가 비어있으면 모든 카테고리를 허용한다")
        void 카테고리_비어있으면_전체_카테고리_허용() {
            // given
            NotificationSetting setting = createSetting(
                    Set.of(),
                    Set.of(RegionSidoCode.SEOUL)
            );
            Policy jobs = createPolicy(1L, Category.JOBS, "11", LocalDate.now().plusDays(5), LocalDateTime.now());
            Policy housing = createPolicy(2L, Category.HOUSING, "11", LocalDate.now().plusDays(5), LocalDateTime.now());

            // when
            List<Policy> result = recommender.filterByInterest(setting, List.of(jobs, housing));

            // then
            assertThat(result).containsExactlyInAnyOrder(jobs, housing);
        }

        @Test
        @DisplayName("지역이 비어있으면 모든 지역을 허용한다")
        void 지역_비어있으면_전체_지역_허용() {
            // given
            NotificationSetting setting = createSetting(
                    Set.of(Category.JOBS),
                    Set.of()
            );
            Policy seoul = createPolicy(1L, Category.JOBS, "11", LocalDate.now().plusDays(5), LocalDateTime.now());
            Policy busan = createPolicy(2L, Category.JOBS, "26", LocalDate.now().plusDays(5), LocalDateTime.now());

            // when
            List<Policy> result = recommender.filterByInterest(setting, List.of(seoul, busan));

            // then
            assertThat(result).containsExactlyInAnyOrder(seoul, busan);
        }

        @Test
        @DisplayName("NATIONAL 또는 null regionCode는 어떤 시도 선택에도 매칭된다")
        void NATIONAL_또는_null_regionCode는_어떤_시도_선택에도_매칭() {
            // given
            NotificationSetting setting = createSetting(
                    Set.of(Category.JOBS),
                    Set.of(RegionSidoCode.SEOUL)
            );
            Policy national = createPolicy(1L, Category.JOBS, "NATIONAL", LocalDate.now().plusDays(5), LocalDateTime.now());
            Policy nullRegion = createPolicy(2L, Category.JOBS, null, LocalDate.now().plusDays(5), LocalDateTime.now());

            // when
            List<Policy> result = recommender.filterByInterest(setting, List.of(national, nullRegion));

            // then
            assertThat(result).containsExactlyInAnyOrder(national, nullRegion);
        }
    }

    @Nested
    @DisplayName("sortAndLimit")
    class SortAndLimit {

        @Test
        @DisplayName("마감 임박순으로 정렬되고 5건으로 절단된다")
        void 마감_임박순으로_정렬되고_5건으로_절단() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Policy d1 = createPolicy(1L, Category.JOBS, "11", LocalDate.now().plusDays(1), now);
            Policy d2 = createPolicy(2L, Category.JOBS, "11", LocalDate.now().plusDays(2), now);
            Policy d3 = createPolicy(3L, Category.JOBS, "11", LocalDate.now().plusDays(3), now);
            Policy d4 = createPolicy(4L, Category.JOBS, "11", LocalDate.now().plusDays(4), now);
            Policy d5 = createPolicy(5L, Category.JOBS, "11", LocalDate.now().plusDays(5), now);
            Policy d6 = createPolicy(6L, Category.JOBS, "11", LocalDate.now().plusDays(6), now);

            // 마감일이 같을 때 신규 등록(createdAt 최신) 우선 검증을 위한 정책
            Policy sameDeadlineNewer = createPolicy(7L, Category.JOBS, "11", LocalDate.now().plusDays(2), now.plusHours(1));

            // when (역순 입력으로 정렬 효과 확인)
            List<Policy> result = recommender.sortAndLimit(List.of(d6, d5, d4, d3, d2, d1, sameDeadlineNewer));

            // then: 마감 임박 순(d1, d2/sameDeadlineNewer, d3, d4) 5건
            assertThat(result).hasSize(5);
            assertThat(result.get(0)).isEqualTo(d1);
            // d2와 sameDeadlineNewer는 같은 마감일(plus 2). createdAt 최신 우선이므로 sameDeadlineNewer가 먼저
            assertThat(result.get(1)).isEqualTo(sameDeadlineNewer);
            assertThat(result.get(2)).isEqualTo(d2);
            assertThat(result.get(3)).isEqualTo(d3);
            assertThat(result.get(4)).isEqualTo(d4);
        }

        @Test
        @DisplayName("마감일이 null인 정책은 후순위로 밀린다")
        void 마감일_null_정책은_후순위() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Policy nullDeadline = createPolicy(1L, Category.JOBS, "11", null, now);
            Policy d1 = createPolicy(2L, Category.JOBS, "11", LocalDate.now().plusDays(10), now);

            // when
            List<Policy> result = recommender.sortAndLimit(List.of(nullDeadline, d1));

            // then
            assertThat(result).containsExactly(d1, nullDeadline);
        }
    }

    // ── 헬퍼 메서드 ──

    private NotificationSetting createSetting(Set<Category> categories, Set<RegionSidoCode> regions) {
        NotificationSetting setting = new NotificationSetting(1L);
        setting.replaceInterestCategories(categories);
        setting.replaceInterestRegions(regions);
        return setting;
    }

    private Policy createPolicy(Long id, Category category, String regionCode,
                                LocalDate applyEnd, LocalDateTime createdAt) {
        Policy policy = Policy.builder()
                .title("정책-" + id)
                .category(category)
                .regionCode(regionCode)
                .applyStart(LocalDate.now().minusDays(30))
                .applyEnd(applyEnd)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        ReflectionTestUtils.setField(policy, "createdAt", createdAt);
        return policy;
    }
}
