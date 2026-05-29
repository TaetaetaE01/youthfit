package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
import com.youthfit.policy.domain.model.SourceType;
import jakarta.persistence.criteria.*;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.*;

@DisplayName("PolicySpecification")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicySpecificationTest {

    @SuppressWarnings("unchecked")
    private final Root<Policy> root = mock(Root.class);

    @SuppressWarnings("unchecked")
    private final CriteriaQuery<Object> query = mock(CriteriaQuery.class);

    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

    @SuppressWarnings({"unchecked", "rawtypes"})
    @BeforeEach
    void setUpSelectCaseChain() {
        // selectCase chain (used by effectiveStatusExpr).
        // Use RETURNS_SELF so Case.when()/otherwise() chain returns the same mock automatically.
        CriteriaBuilder.Case caseExpr = mock(CriteriaBuilder.Case.class, RETURNS_SELF);
        Expression<String> caseAs = mock(Expression.class);
        doReturn(caseExpr).when(cb).selectCase();
        doReturn(caseAs).when(caseExpr).as(String.class);
        // status equality predicate (against the case expression)
        given(cb.equal(eq(caseAs), anyString())).willReturn(mock(Predicate.class));

        // predicates used inside selectCase WHEN clauses
        given(cb.isNotNull(any(Expression.class))).willReturn(mock(Predicate.class));
        given(cb.lessThan(any(Expression.class), any(java.time.LocalDate.class))).willReturn(mock(Predicate.class));
        given(cb.greaterThan(any(Expression.class), any(java.time.LocalDate.class))).willReturn(mock(Predicate.class));
        given(cb.lessThan(any(Expression.class), any(Integer.class))).willReturn(mock(Predicate.class));
        given(cb.equal(any(Expression.class), any(Integer.class))).willReturn(mock(Predicate.class));
        given(cb.and(any(Predicate.class), any(Predicate.class))).willReturn(mock(Predicate.class));
    }

    @Test
    @DisplayName("withFilters: 모든 필터가 null이면 빈 조건으로 AND를 생성한다")
    void withFilters_allNull_createsEmptyAnd() {
        // given
        Predicate conjunction = mock(Predicate.class);
        Path<Object> createdAtPath = mock(Path.class);
        Order descOrder = mock(Order.class);
        given(cb.and(any(Predicate[].class))).willReturn(conjunction);
        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.desc(createdAtPath)).willReturn(descOrder);

        Specification<Policy> spec = PolicySpecification.withFilters(RegionFilter.of(null), null, null, null);

        // when
        Predicate result = spec.toPredicate(root, query, cb);

        // then
        assertThat(result).isEqualTo(conjunction);
        then(root).should(never()).get(eq("regionCode"));
        then(root).should(never()).get(eq("category"));
    }

    @Test
    @DisplayName("withFilters: regionCode만 지정하면 regionCode 조건만 추가된다")
    void withFilters_onlyRegionCode_addsRegionPredicate() {
        // given
        Path<Object> path = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        Order descOrder = mock(Order.class);
        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get(anyString())).willReturn(path);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.equal(any(), anyString())).willReturn(predicate);
        given(cb.like(any(jakarta.persistence.criteria.Expression.class), anyString())).willReturn(predicate);
        given(cb.or(any(Predicate[].class))).willReturn(predicate);
        Expression<String> concatExpr = mock(Expression.class);
        given(cb.concat(anyString(), any(jakarta.persistence.criteria.Expression.class))).willReturn(concatExpr);
        given(cb.concat(any(jakarta.persistence.criteria.Expression.class), anyString())).willReturn(concatExpr);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(predicate);

        Specification<Policy> spec = PolicySpecification.withFilters(
                RegionFilter.of(java.util.List.of("11")), null, null, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().equal(path, "11");
    }

    @Test
    @DisplayName("withFilters: status가 OPEN이면 applyEnd asc + createdAt desc 정렬을 적용한다")
    void withFilters_open_appliesDeadlineAscOrdering() {
        // given
        Path<Object> applyEndPath = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Order ascOrder = mock(Order.class);
        Order descOrder = mock(Order.class);
        Predicate combined = mock(Predicate.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("applyEnd")).willReturn(applyEndPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.coalesce(eq(applyEndPath), any(java.time.LocalDate.class))).willReturn(coalesced);
        given(cb.asc(coalesced)).willReturn(ascOrder);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(combined);

        Specification<Policy> spec = PolicySpecification.withFilters(RegionFilter.of(null), null, PolicyStatus.OPEN, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().asc(coalesced);
        then(cb).should().desc(createdAtPath);
        then(query).should().orderBy(java.util.List.of(ascOrder, descOrder));
    }

    @Test
    @DisplayName("withFilters: status가 UPCOMING이면 applyStart asc + createdAt desc 정렬을 적용한다")
    void withFilters_upcoming_appliesApplyStartAscOrdering() {
        // given
        Path<Object> applyStartPath = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Order ascOrder = mock(Order.class);
        Order descOrder = mock(Order.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("applyStart")).willReturn(applyStartPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.coalesce(eq(applyStartPath), any(java.time.LocalDate.class))).willReturn(coalesced);
        given(cb.asc(coalesced)).willReturn(ascOrder);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(mock(Predicate.class));

        Specification<Policy> spec = PolicySpecification.withFilters(RegionFilter.of(null), null, PolicyStatus.UPCOMING, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().asc(coalesced);
        then(query).should().orderBy(java.util.List.of(ascOrder, descOrder));
    }

    @Test
    @DisplayName("withFilters: status가 CLOSED이면 applyEnd desc + createdAt desc 정렬을 적용한다")
    void withFilters_closed_appliesApplyEndDescOrdering() {
        // given
        Path<Object> applyEndPath = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Order descEndOrder = mock(Order.class);
        Order descCreatedOrder = mock(Order.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("applyEnd")).willReturn(applyEndPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.coalesce(eq(applyEndPath), any(java.time.LocalDate.class))).willReturn(coalesced);
        given(cb.desc(coalesced)).willReturn(descEndOrder);
        given(cb.desc(createdAtPath)).willReturn(descCreatedOrder);
        given(cb.and(any(Predicate[].class))).willReturn(mock(Predicate.class));

        Specification<Policy> spec = PolicySpecification.withFilters(RegionFilter.of(null), null, PolicyStatus.CLOSED, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().desc(coalesced);
        then(query).should().orderBy(java.util.List.of(descEndOrder, descCreatedOrder));
    }

    @Test
    @DisplayName("withFilters: status가 null이면 createdAt desc 단일 정렬만 적용된다")
    void withFilters_nullStatus_appliesCreatedAtDesc() {
        // given
        Path<Object> createdAtPath = mock(Path.class);
        Order descOrder = mock(Order.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(mock(Predicate.class));

        Specification<Policy> spec = PolicySpecification.withFilters(RegionFilter.of(null), null, null, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(query).should().orderBy(java.util.List.of(descOrder));
    }

    @Test
    @DisplayName("withFilters: count 쿼리에서는 정렬을 적용하지 않는다")
    void withFilters_countQuery_doesNotApplyOrder() {
        // given
        given(query.getResultType()).willReturn((Class) Long.class);
        given(cb.and(any(Predicate[].class))).willReturn(mock(Predicate.class));

        Specification<Policy> spec = PolicySpecification.withFilters(RegionFilter.of(null), null, PolicyStatus.OPEN, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(query).should(never()).orderBy(any(java.util.List.class));
    }

    @Test
    @DisplayName("withFilters: source가 주어지면 EXISTS 서브쿼리 predicate를 추가한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void withFilters_withSource_addsExistsSubquery() {
        // given
        Predicate conjunction = mock(Predicate.class);
        Path<Object> createdAtPath = mock(Path.class);
        Path<Object> rootIdPath = mock(Path.class);
        Order descOrder = mock(Order.class);
        Subquery<Long> subquery = mock(Subquery.class, RETURNS_DEEP_STUBS);
        Root<PolicySource> sourceRoot = mock(Root.class, RETURNS_DEEP_STUBS);
        Predicate existsPredicate = mock(Predicate.class);

        given(cb.and(any(Predicate[].class))).willReturn(conjunction);
        given(query.getResultType()).willReturn((Class) Policy.class);
        given(query.subquery(Long.class)).willReturn(subquery);
        given(subquery.from(PolicySource.class)).willReturn(sourceRoot);
        given(subquery.select(any())).willReturn(subquery);
        given(subquery.where(any(Predicate.class), any(Predicate.class))).willReturn(subquery);
        given(cb.exists(subquery)).willReturn(existsPredicate);
        given(cb.equal(any(jakarta.persistence.criteria.Expression.class), any(jakarta.persistence.criteria.Expression.class))).willReturn(mock(Predicate.class));
        given(cb.equal(any(jakarta.persistence.criteria.Expression.class), any(SourceType.class))).willReturn(mock(Predicate.class));
        given(root.get("id")).willReturn(rootIdPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.desc(createdAtPath)).willReturn(descOrder);

        Specification<Policy> spec = PolicySpecification.withFilters(
                RegionFilter.of(null), null, null,
                SourceType.BOKJIRO_CENTRAL);

        // when
        spec.toPredicate(root, query, cb);

        // then — exists 서브쿼리가 한 번 이상 생성되었음
        then(query).should().subquery(Long.class);
        then(cb).should().exists(subquery);
        then(subquery).should().select(any());
        then(subquery).should().where(any(Predicate.class), any(Predicate.class));
        then(sourceRoot).should().get("sourceType");
    }

    @Test
    @DisplayName("withFilters: source가 null이면 EXISTS 서브쿼리를 만들지 않는다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void withFilters_withoutSource_skipsExistsSubquery() {
        // given
        Predicate conjunction = mock(Predicate.class);
        Path<Object> createdAtPath = mock(Path.class);
        Order descOrder = mock(Order.class);
        given(cb.and(any(Predicate[].class))).willReturn(conjunction);
        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.desc(createdAtPath)).willReturn(descOrder);

        Specification<Policy> spec = PolicySpecification.withFilters(
                RegionFilter.of(null), null, null, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(query).should(never()).subquery(Long.class);
        then(cb).should(never()).exists(any());
    }

    @Test
    @DisplayName("withKeyword: 키워드만 지정하면 title/summary OR 조건과 createdAt desc 정렬을 적용한다")
    void withKeyword_onlyKeyword_addsLikeOrPredicateAndCreatedAtDesc() {
        // given
        Path<String> titlePath = mock(Path.class);
        Path<String> summaryPath = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Expression<String> lowerTitle = mock(Expression.class);
        Expression<String> lowerSummary = mock(Expression.class);
        Predicate likeTitle = mock(Predicate.class);
        Predicate likeSummary = mock(Predicate.class);
        Predicate orPredicate = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);
        Order descOrder = mock(Order.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.<String>get("title")).willReturn(titlePath);
        given(root.<String>get("summary")).willReturn(summaryPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.lower(titlePath)).willReturn(lowerTitle);
        given(cb.lower(summaryPath)).willReturn(lowerSummary);
        given(cb.like(lowerTitle, "%주거%")).willReturn(likeTitle);
        given(cb.like(lowerSummary, "%주거%")).willReturn(likeSummary);
        given(cb.or(likeTitle, likeSummary)).willReturn(orPredicate);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(combined);

        Specification<Policy> spec = PolicySpecification.withKeyword("주거", null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().or(likeTitle, likeSummary);
        then(query).should().orderBy(java.util.List.of(descOrder));
    }

    @Test
    @DisplayName("withKeyword: status가 OPEN이면 status 조건과 OPEN 정렬을 함께 적용한다")
    void withKeyword_withOpenStatus_addsStatusPredicateAndOpenOrdering() {
        // given
        Path<String> titlePath = mock(Path.class);
        Path<String> summaryPath = mock(Path.class);
        Path<Object> applyEndPath = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Order ascOrder = mock(Order.class);
        Order descOrder = mock(Order.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.<String>get("title")).willReturn(titlePath);
        given(root.<String>get("summary")).willReturn(summaryPath);
        given(root.get("applyEnd")).willReturn(applyEndPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.lower(any())).willReturn(mock(Expression.class));
        given(cb.like(any(Expression.class), anyString())).willReturn(mock(Predicate.class));
        given(cb.or(any(Predicate.class), any(Predicate.class))).willReturn(mock(Predicate.class));
        given(cb.coalesce(eq(applyEndPath), any(java.time.LocalDate.class))).willReturn(coalesced);
        given(cb.asc(coalesced)).willReturn(ascOrder);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(mock(Predicate.class));

        Specification<Policy> spec = PolicySpecification.withKeyword("주거", PolicyStatus.OPEN);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().asc(coalesced);
        then(query).should().orderBy(java.util.List.of(ascOrder, descOrder));
    }

    @Nested
    @DisplayName("withFilters(regionFilter) — 매칭 분기")
    class RegionMatching {

        @Test
        @DisplayName("비활성 필터: 지역 관련 predicate 가 추가되지 않는다 (status/category 만)")
        void inactiveFilter_noRegionPredicate() {
            // given
            given(query.getResultType()).willReturn((Class) Long.class); // skip orderBy branch
            Predicate any = mock(Predicate.class);
            given(cb.equal(any(), anyString())).willReturn(any);
            given(cb.like(any(), anyString())).willReturn(any);
            given(cb.concat(anyString(), any(jakarta.persistence.criteria.Expression.class))).willReturn(mock(jakarta.persistence.criteria.Expression.class));
            given(cb.concat(any(jakarta.persistence.criteria.Expression.class), anyString())).willReturn(mock(jakarta.persistence.criteria.Expression.class));
            given(cb.or(any(Predicate[].class))).willReturn(any);
            given(cb.and(any(Predicate[].class))).willReturn(any);
            Path<Object> path = mock(Path.class);
            given(root.get(anyString())).willReturn(path);

            // when
            PolicySpecification.withFilters(RegionFilter.of(null), null, null, null).toPredicate(root, query, cb);

            // then — region 쪽 cb.or 가 호출되지 않음을 확인하기 위해, 적어도 그 분기에 진입했을 때만 사용하는 메서드(cb.concat) 호출 회수가 0 이어야 한다
            then(cb).should(times(0)).concat(anyString(), any(jakarta.persistence.criteria.Expression.class));
        }

        @Test
        @DisplayName("NATIONWIDE 단독: regionCode == '전국' predicate 만 추가된다")
        void nationwideOnly_addsEqualNationwidePredicate() {
            // given
            given(query.getResultType()).willReturn((Class) Long.class); // skip orderBy branch
            Predicate eq = mock(Predicate.class);
            given(cb.equal(any(), anyString())).willReturn(eq);
            given(cb.and(any(Predicate[].class))).willReturn(eq);
            Path<Object> path = mock(Path.class);
            given(root.get("regionCode")).willReturn(path);

            // when
            PolicySpecification.withFilters(
                    RegionFilter.of(java.util.List.of("NATIONWIDE")), null, null, null)
                    .toPredicate(root, query, cb);

            // then — cb.equal(regionCode, "전국") 이 호출되었음을 검증
            then(cb).should().equal(eq(path), eq("전국"));
        }

        @Test
        @DisplayName("시·도 코드: regionCode 정확/prefix 매칭 + regionCodes CSV LIKE 가 모두 OR 로 추가된다")
        void sidoCode_addsMultiplePredicates() {
            // given
            given(query.getResultType()).willReturn((Class) Long.class); // skip orderBy branch
            Predicate p = mock(Predicate.class);
            given(cb.equal(any(), anyString())).willReturn(p);
            given(cb.like(any(jakarta.persistence.criteria.Expression.class), anyString())).willReturn(p);
            given(cb.or(any(Predicate[].class))).willReturn(p);
            given(cb.and(any(Predicate[].class))).willReturn(p);
            jakarta.persistence.criteria.Expression<String> concatExpr = mock(jakarta.persistence.criteria.Expression.class);
            given(cb.concat(anyString(), any(jakarta.persistence.criteria.Expression.class))).willReturn(concatExpr);
            given(cb.concat(any(jakarta.persistence.criteria.Expression.class), anyString())).willReturn(concatExpr);
            Path<Object> path = mock(Path.class);
            given(root.get(anyString())).willReturn(path);

            // when
            PolicySpecification.withFilters(
                    RegionFilter.of(java.util.List.of("11")), null, null, null)
                    .toPredicate(root, query, cb);

            // then — like 가 시·도 prefix(11%) + CSV 패딩(",11," ",11xxx,") 으로 최소 3회 이상 호출됨
            then(cb).should(atLeast(3)).like(any(jakarta.persistence.criteria.Expression.class), anyString());
        }

        @Test
        @DisplayName("시·군·구 코드: regionCode 정확 매칭 + CSV 콤마 패딩 LIKE 가 추가된다")
        void sigunguCode_addsExactAndCsvLike() {
            // given
            given(query.getResultType()).willReturn((Class) Long.class); // skip orderBy branch
            Predicate p = mock(Predicate.class);
            given(cb.equal(any(), anyString())).willReturn(p);
            given(cb.like(any(jakarta.persistence.criteria.Expression.class), anyString())).willReturn(p);
            given(cb.or(any(Predicate[].class))).willReturn(p);
            given(cb.and(any(Predicate[].class))).willReturn(p);
            jakarta.persistence.criteria.Expression<String> concatExpr = mock(jakarta.persistence.criteria.Expression.class);
            given(cb.concat(anyString(), any(jakarta.persistence.criteria.Expression.class))).willReturn(concatExpr);
            given(cb.concat(any(jakarta.persistence.criteria.Expression.class), anyString())).willReturn(concatExpr);
            Path<Object> path = mock(Path.class);
            given(root.get(anyString())).willReturn(path);

            // when
            PolicySpecification.withFilters(
                    RegionFilter.of(java.util.List.of("11680")), null, null, null)
                    .toPredicate(root, query, cb);

            // then — 콤마 패딩 LIKE %,11680,% 가 호출되어야 함
            then(cb).should().like(any(jakarta.persistence.criteria.Expression.class), eq("%,11680,%"));
        }
    }
}
