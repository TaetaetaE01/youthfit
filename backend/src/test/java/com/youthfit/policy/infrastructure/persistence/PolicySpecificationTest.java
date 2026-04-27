package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@DisplayName("PolicySpecification")
class PolicySpecificationTest {

    @SuppressWarnings("unchecked")
    private final Root<Policy> root = mock(Root.class);

    @SuppressWarnings("unchecked")
    private final CriteriaQuery<Object> query = mock(CriteriaQuery.class);

    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

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

        Specification<Policy> spec = PolicySpecification.withFilters(null, null, null);

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
        given(root.get("regionCode")).willReturn(path);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.equal(path, "11")).willReturn(predicate);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(predicate);

        Specification<Policy> spec = PolicySpecification.withFilters("11", null, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().equal(path, "11");
    }

    @Test
    @DisplayName("withFilters: status가 OPEN이면 applyEnd asc + createdAt desc 정렬을 적용한다")
    void withFilters_open_appliesDeadlineAscOrdering() {
        // given
        Path<Object> statusPath = mock(Path.class);
        Path<Object> applyEndPath = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Order ascOrder = mock(Order.class);
        Order descOrder = mock(Order.class);
        Predicate combined = mock(Predicate.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("status")).willReturn(statusPath);
        given(root.get("applyEnd")).willReturn(applyEndPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.equal(statusPath, PolicyStatus.OPEN)).willReturn(mock(Predicate.class));
        given(cb.coalesce(eq(applyEndPath), any(java.time.LocalDate.class))).willReturn(coalesced);
        given(cb.asc(coalesced)).willReturn(ascOrder);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(combined);

        Specification<Policy> spec = PolicySpecification.withFilters(null, null, PolicyStatus.OPEN);

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
        Path<Object> statusPath = mock(Path.class);
        Path<Object> applyStartPath = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Order ascOrder = mock(Order.class);
        Order descOrder = mock(Order.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("status")).willReturn(statusPath);
        given(root.get("applyStart")).willReturn(applyStartPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.equal(statusPath, PolicyStatus.UPCOMING)).willReturn(mock(Predicate.class));
        given(cb.coalesce(eq(applyStartPath), any(java.time.LocalDate.class))).willReturn(coalesced);
        given(cb.asc(coalesced)).willReturn(ascOrder);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(mock(Predicate.class));

        Specification<Policy> spec = PolicySpecification.withFilters(null, null, PolicyStatus.UPCOMING);

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
        Path<Object> statusPath = mock(Path.class);
        Path<Object> applyEndPath = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Order descEndOrder = mock(Order.class);
        Order descCreatedOrder = mock(Order.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("status")).willReturn(statusPath);
        given(root.get("applyEnd")).willReturn(applyEndPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.equal(statusPath, PolicyStatus.CLOSED)).willReturn(mock(Predicate.class));
        given(cb.coalesce(eq(applyEndPath), any(java.time.LocalDate.class))).willReturn(coalesced);
        given(cb.desc(coalesced)).willReturn(descEndOrder);
        given(cb.desc(createdAtPath)).willReturn(descCreatedOrder);
        given(cb.and(any(Predicate[].class))).willReturn(mock(Predicate.class));

        Specification<Policy> spec = PolicySpecification.withFilters(null, null, PolicyStatus.CLOSED);

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

        Specification<Policy> spec = PolicySpecification.withFilters(null, null, null);

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

        Specification<Policy> spec = PolicySpecification.withFilters(null, null, PolicyStatus.OPEN);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(query).should(never()).orderBy(any(java.util.List.class));
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
        Path<Object> statusPath = mock(Path.class);
        Path<Object> applyEndPath = mock(Path.class);
        Path<Object> createdAtPath = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Order ascOrder = mock(Order.class);
        Order descOrder = mock(Order.class);

        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.<String>get("title")).willReturn(titlePath);
        given(root.<String>get("summary")).willReturn(summaryPath);
        given(root.get("status")).willReturn(statusPath);
        given(root.get("applyEnd")).willReturn(applyEndPath);
        given(root.get("createdAt")).willReturn(createdAtPath);
        given(cb.lower(any())).willReturn(mock(Expression.class));
        given(cb.like(any(Expression.class), anyString())).willReturn(mock(Predicate.class));
        given(cb.or(any(Predicate.class), any(Predicate.class))).willReturn(mock(Predicate.class));
        given(cb.equal(statusPath, PolicyStatus.OPEN)).willReturn(mock(Predicate.class));
        given(cb.coalesce(eq(applyEndPath), any(java.time.LocalDate.class))).willReturn(coalesced);
        given(cb.asc(coalesced)).willReturn(ascOrder);
        given(cb.desc(createdAtPath)).willReturn(descOrder);
        given(cb.and(any(Predicate[].class))).willReturn(mock(Predicate.class));

        Specification<Policy> spec = PolicySpecification.withKeyword("주거", PolicyStatus.OPEN);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().equal(statusPath, PolicyStatus.OPEN);
        then(cb).should().asc(coalesced);
        then(query).should().orderBy(java.util.List.of(ascOrder, descOrder));
    }
}
