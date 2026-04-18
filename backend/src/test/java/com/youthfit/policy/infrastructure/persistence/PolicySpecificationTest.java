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
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);

    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

    @Test
    @DisplayName("모든 필터가 null이면 빈 조건으로 AND를 생성한다")
    void allNull_createsEmptyAnd() {
        // given
        Predicate conjunction = mock(Predicate.class);
        given(cb.and(any(Predicate[].class))).willReturn(conjunction);

        Specification<Policy> spec = PolicySpecification.withFiltersAndSort(null, null, null, null);

        // when
        Predicate result = spec.toPredicate(root, query, cb);

        // then
        assertThat(result).isEqualTo(conjunction);
        then(root).should(never()).get(anyString());
    }

    @Test
    @DisplayName("regionCode만 지정하면 regionCode 조건만 추가된다")
    void onlyRegionCode_addsRegionPredicate() {
        // given
        Path<Object> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        given(root.get("regionCode")).willReturn(path);
        given(cb.equal(path, "11")).willReturn(predicate);
        given(cb.and(any(Predicate[].class))).willReturn(predicate);

        Specification<Policy> spec = PolicySpecification.withFiltersAndSort("11", null, null, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().equal(path, "11");
    }

    @Test
    @DisplayName("category만 지정하면 category 조건만 추가된다")
    void onlyCategory_addsCategoryPredicate() {
        // given
        Path<Object> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        given(root.get("category")).willReturn(path);
        given(cb.equal(path, Category.JOBS)).willReturn(predicate);
        given(cb.and(any(Predicate[].class))).willReturn(predicate);

        Specification<Policy> spec = PolicySpecification.withFiltersAndSort(null, Category.JOBS, null, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().equal(path, Category.JOBS);
    }

    @Test
    @DisplayName("status만 지정하면 status 조건만 추가된다")
    void onlyStatus_addsStatusPredicate() {
        // given
        Path<Object> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        given(root.get("status")).willReturn(path);
        given(cb.equal(path, PolicyStatus.OPEN)).willReturn(predicate);
        given(cb.and(any(Predicate[].class))).willReturn(predicate);

        Specification<Policy> spec = PolicySpecification.withFiltersAndSort(null, null, PolicyStatus.OPEN, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().equal(path, PolicyStatus.OPEN);
    }

    @Test
    @DisplayName("모든 필터가 지정되면 3개 조건이 모두 추가된다")
    void allFilters_addsAllPredicates() {
        // given
        Path<Object> regionPath = mock(Path.class);
        Path<Object> categoryPath = mock(Path.class);
        Path<Object> statusPath = mock(Path.class);
        Predicate p1 = mock(Predicate.class);
        Predicate p2 = mock(Predicate.class);
        Predicate p3 = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);

        given(root.get("regionCode")).willReturn(regionPath);
        given(root.get("category")).willReturn(categoryPath);
        given(root.get("status")).willReturn(statusPath);
        given(cb.equal(regionPath, "11")).willReturn(p1);
        given(cb.equal(categoryPath, Category.JOBS)).willReturn(p2);
        given(cb.equal(statusPath, PolicyStatus.OPEN)).willReturn(p3);
        given(cb.and(any(Predicate[].class))).willReturn(combined);

        Specification<Policy> spec = PolicySpecification.withFiltersAndSort("11", Category.JOBS, PolicyStatus.OPEN, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        then(cb).should().equal(regionPath, "11");
        then(cb).should().equal(categoryPath, Category.JOBS);
        then(cb).should().equal(statusPath, PolicyStatus.OPEN);
    }
}
