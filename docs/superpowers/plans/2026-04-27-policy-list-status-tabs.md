# Policy List — Status Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책 목록 페이지를 `진행중 → 예정 → 마감` 단일 선택 탭 구조로 개편하고, 각 탭에 시간 축 기반 정렬을 고정하면서 `PolicySortType` enum과 사용자 정렬 dropdown을 제거한다.

**Architecture:** 백엔드는 `PolicySpecification`을 `PolicyStatus` 인자 기반으로 분기 정렬하도록 단순화하고 `sortType` 쿼리 파라미터를 제거한다. 프론트엔드는 status 칩 토글을 Tab Bar로 교체하고, URL `searchParams`의 `status`가 비어 있으면 `OPEN`으로 간주해 default 활성 탭을 만든다. 키워드 검색 API에도 `status`를 전달해 탭 정렬을 동일하게 적용한다.

**Tech Stack:** Java 21 / Spring Boot 4.0.5 / JPA Specification, React 19 / TypeScript / TanStack Query / React Router v7 / Tailwind CSS

**Spec:** `docs/superpowers/specs/2026-04-27-policy-list-status-tabs-design.md`

---

## File Structure

**Backend (수정/삭제)**

| 경로 | 변경 |
|------|------|
| `backend/src/main/java/com/youthfit/policy/domain/model/PolicySortType.java` | **삭제** |
| `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java` | sortType 인자 제거, status 인자 기반 정렬, statusWeight 제거, FAR_PAST 상수 추가 |
| `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java` | sortType 인자 제거, search에 status 추가 |
| `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java` | 새 시그니처에 맞춰 호출 수정 |
| `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java` | sortType 파라미터 제거, search에 status 추가 |
| `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java` | sortType 파라미터 제거, search에 status 파라미터 추가 |
| `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyApi.java` | sortType 파라미터 제거 + Swagger 문서 갱신, search에 status 파라미터 추가 |
| `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationTest.java` | 새 시그니처에 맞춰 갱신 + status별 정렬 케이스 추가 |
| `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java` | 새 시그니처에 맞춰 갱신 |
| `backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyControllerTest.java` | sortType 파라미터 제거, search에 status 케이스 추가 |

**Frontend (수정)**

| 경로 | 변경 |
|------|------|
| `frontend/src/types/policy.ts` | `PolicySortType` 타입 export 제거 |
| `frontend/src/apis/policy.api.ts` | sortType 쿼리 파라미터 제거, search API에 status 추가 |
| `frontend/src/hooks/queries/usePolicies.ts` | sortType 인자·queryKey 제거, search 분기에서 status 전달 |
| `frontend/src/pages/PolicyListPage.tsx` | Tab Bar 도입, default status=OPEN, sort dropdown 제거, status 활성 필터 칩 제거, mobile filter sheet에서 status 영역 제거 |

---

## Task 1: 백엔드 — PolicySpecification status 기반 정렬로 교체

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java`
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java`
- Test: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationTest.java`

- [ ] **Step 1: PolicySpecification 새 메서드 시그니처 적용**

`backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java` 전체를 다음으로 교체:

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PolicySpecification {

    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);
    private static final LocalDate FAR_PAST = LocalDate.of(1, 1, 1);

    private PolicySpecification() {
    }

    public static Specification<Policy> withFilters(String regionCode, Category category, PolicyStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (regionCode != null) {
                predicates.add(cb.equal(root.get("regionCode"), regionCode));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            applyOrder(root, query, cb, status);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Policy> withKeyword(String keyword, PolicyStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String pattern = "%" + keyword.toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("summary")), pattern)
            ));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            applyOrder(root, query, cb, status);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void applyOrder(Root<Policy> root, CriteriaQuery<?> query,
                                   CriteriaBuilder cb, PolicyStatus status) {
        if (query == null) {
            return;
        }
        Class<?> resultType = query.getResultType();
        if (resultType == Long.class || resultType == long.class) {
            return; // count query
        }
        query.orderBy(buildOrders(root, cb, status));
    }

    private static List<Order> buildOrders(Root<Policy> root, CriteriaBuilder cb, PolicyStatus status) {
        if (status == null) {
            return List.of(cb.desc(root.get("createdAt")));
        }
        return switch (status) {
            case OPEN -> List.of(
                    cb.asc(cb.coalesce(root.get("applyEnd"), FAR_FUTURE)),
                    cb.desc(root.get("createdAt"))
            );
            case UPCOMING -> List.of(
                    cb.asc(cb.coalesce(root.get("applyStart"), FAR_FUTURE)),
                    cb.desc(root.get("createdAt"))
            );
            case CLOSED -> List.of(
                    cb.desc(cb.coalesce(root.get("applyEnd"), FAR_PAST)),
                    cb.desc(root.get("createdAt"))
            );
        };
    }
}
```

- [ ] **Step 2: PolicyRepository 인터페이스 갱신**

`backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java` 전체를 다음으로 교체:

```java
package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PolicyRepository {

    Optional<Policy> findById(Long id);

    Page<Policy> findAllByFilters(String regionCode, Category category, PolicyStatus status,
                                   Pageable pageable);

    Page<Policy> searchByKeyword(String keyword, PolicyStatus status, Pageable pageable);

    Policy save(Policy policy);
}
```

- [ ] **Step 3: PolicyRepositoryImpl 갱신**

`backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java` 전체를 다음으로 교체:

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PolicyRepositoryImpl implements PolicyRepository {

    private final PolicyJpaRepository jpaRepository;

    public PolicyRepositoryImpl(PolicyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Policy> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Page<Policy> findAllByFilters(String regionCode, Category category, PolicyStatus status,
                                         Pageable pageable) {
        return jpaRepository.findAll(
                PolicySpecification.withFilters(regionCode, category, status), pageable);
    }

    @Override
    public Page<Policy> searchByKeyword(String keyword, PolicyStatus status, Pageable pageable) {
        return jpaRepository.findAll(
                PolicySpecification.withKeyword(keyword, status), pageable);
    }

    @Override
    public Policy save(Policy policy) {
        return jpaRepository.save(policy);
    }
}
```

- [ ] **Step 4: PolicyQueryService 갱신**

`backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java` 전체를 다음으로 교체:

```java
package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final PolicyRepository policyRepository;
    private final PolicySourceRepository policySourceRepository;

    public PolicyPageResult findPoliciesByFilters(String regionCode, Category category,
                                                  PolicyStatus status,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Policy> policyPage = policyRepository.findAllByFilters(regionCode, category, status, pageable);

        return toPageResult(policyPage);
    }

    public PolicyDetailResult findPolicyById(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다: " + policyId));
        String sourceUrl = policySourceRepository.findFirstByPolicyId(policyId)
                .map(src -> src.getSourceUrl())
                .orElse(null);
        return PolicyDetailResult.from(policy, sourceUrl);
    }

    public PolicyPageResult searchPoliciesByKeyword(String keyword, PolicyStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Policy> policyPage = policyRepository.searchByKeyword(keyword, status, pageable);

        return toPageResult(policyPage);
    }

    private PolicyPageResult toPageResult(Page<Policy> policyPage) {
        return new PolicyPageResult(
                policyPage.getContent().stream().map(PolicySummaryResult::from).toList(),
                policyPage.getTotalElements(),
                policyPage.getNumber(),
                policyPage.getSize(),
                policyPage.getTotalPages(),
                policyPage.hasNext()
        );
    }
}
```

- [ ] **Step 5: PolicySpecificationTest 갱신**

`backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationTest.java` 전체를 다음으로 교체:

```java
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
        given(cb.and(any(Predicate[].class))).willReturn(conjunction);
        given(query.getResultType()).willReturn((Class) Policy.class);

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
        Predicate predicate = mock(Predicate.class);
        given(query.getResultType()).willReturn((Class) Policy.class);
        given(root.get("regionCode")).willReturn(path);
        given(cb.equal(path, "11")).willReturn(predicate);
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
```

- [ ] **Step 6: PolicyQueryServiceTest 갱신**

`backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java` 전체를 다음으로 교체:

```java
package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@DisplayName("PolicyQueryService")
@ExtendWith(MockitoExtension.class)
class PolicyQueryServiceTest {

    @InjectMocks
    private PolicyQueryService policyQueryService;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicySourceRepository policySourceRepository;

    @Nested
    @DisplayName("findPolicyById")
    class FindPolicyById {

        @Test
        @DisplayName("존재하는 정책 ID로 조회하면 상세 결과를 반환한다")
        void exists_returnsDetail() {
            // given
            Policy policy = createMockPolicy();
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(policySourceRepository.findFirstByPolicyId(1L)).willReturn(Optional.<PolicySource>empty());

            // when
            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            // then
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.title()).isEqualTo("청년 주거 지원");
            assertThat(result.category()).isEqualTo(Category.HOUSING);
        }

        @Test
        @DisplayName("존재하지 않는 정책 ID로 조회하면 NOT_FOUND 예외가 발생한다")
        void notExists_throwsNotFoundException() {
            // given
            given(policyRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> policyQueryService.findPolicyById(999L))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("findPoliciesByFilters")
    class FindPoliciesByFilters {

        @Test
        @DisplayName("status=OPEN 필터로 정책 목록을 페이징 조회한다")
        void withOpenStatus_returnsFilteredPage() {
            // given
            Page<Policy> mockPage = new PageImpl<>(
                    List.of(createMockPolicy()),
                    Pageable.ofSize(20), 1);
            given(policyRepository.findAllByFilters(
                    eq("11"), eq(Category.HOUSING), eq(PolicyStatus.OPEN), any(Pageable.class)))
                    .willReturn(mockPage);

            // when
            PolicyPageResult result = policyQueryService.findPoliciesByFilters(
                    "11", Category.HOUSING, PolicyStatus.OPEN, 0, 20);

            // then
            assertThat(result.policies()).hasSize(1);
            assertThat(result.totalCount()).isEqualTo(1);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("필터가 모두 null이어도 정상 동작한다")
        void allNull_returnsPage() {
            // given
            Page<Policy> emptyPage = Page.empty();
            given(policyRepository.findAllByFilters(
                    isNull(), isNull(), isNull(), any(Pageable.class)))
                    .willReturn(emptyPage);

            // when
            PolicyPageResult result = policyQueryService.findPoliciesByFilters(
                    null, null, null, 0, 20);

            // then
            assertThat(result.policies()).isEmpty();
            assertThat(result.totalCount()).isZero();
        }
    }

    @Nested
    @DisplayName("searchPoliciesByKeyword")
    class SearchPoliciesByKeyword {

        @Test
        @DisplayName("키워드 + status=OPEN으로 검색하면 status를 그대로 전달한다")
        void withKeywordAndStatus_passesStatus() {
            // given
            Page<Policy> mockPage = new PageImpl<>(
                    List.of(createMockPolicy()),
                    Pageable.ofSize(20), 1);
            given(policyRepository.searchByKeyword(eq("주거"), eq(PolicyStatus.OPEN), any(Pageable.class)))
                    .willReturn(mockPage);

            // when
            PolicyPageResult result = policyQueryService.searchPoliciesByKeyword("주거", PolicyStatus.OPEN, 0, 20);

            // then
            assertThat(result.policies()).hasSize(1);
            assertThat(result.policies().getFirst().title()).contains("주거");
        }

        @Test
        @DisplayName("status가 null이어도 키워드 검색은 정상 동작한다")
        void nullStatus_returnsResults() {
            // given
            Page<Policy> mockPage = new PageImpl<>(
                    List.of(createMockPolicy()),
                    Pageable.ofSize(20), 1);
            given(policyRepository.searchByKeyword(eq("주거"), isNull(), any(Pageable.class)))
                    .willReturn(mockPage);

            // when
            PolicyPageResult result = policyQueryService.searchPoliciesByKeyword("주거", null, 0, 20);

            // then
            assertThat(result.policies()).hasSize(1);
        }
    }

    // ── 헬퍼 메서드 ──

    private Policy createMockPolicy() {
        Policy policy = Policy.builder()
                .title("청년 주거 지원")
                .summary("월세 지원 프로그램")
                .category(Category.HOUSING)
                .regionCode("11")
                .applyStart(LocalDate.of(2026, 1, 1))
                .applyEnd(LocalDate.of(2026, 12, 31))
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        return policy;
    }
}
```

- [ ] **Step 7: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests PolicySpecificationTest --tests PolicyQueryServiceTest -i`
Expected: 두 테스트 클래스 모두 PASS. 컴파일 에러는 다음 task에서 해결할 PolicyController/PolicyApi 변경 전에는 일부 컴파일 실패할 수 있음 — 이 step은 이후 Task 2 step 4와 묶어서 한 번에 검증해도 됨.

> 참고: Controller/Api는 아직 sortType을 사용 중이므로 전체 빌드는 Task 2 완료 전까지 실패할 수 있다. 이 단계에서는 단위 테스트 단독 실행으로 PolicySpecification·PolicyQueryService 변경의 정합성만 확인한다.

- [ ] **Step 8: 진행 상황 검토 (커밋은 Task 2와 묶음)**

Task 2까지 완료한 뒤 한 번에 커밋한다. 이 단계에서는 별도 git 작업 없음.

---

## Task 2: 백엔드 — Controller/Api에서 sortType 제거 + search에 status 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java`
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyApi.java`
- Test: `backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyControllerTest.java`

- [ ] **Step 1: PolicyApi 갱신**

`backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyApi.java` 전체를 다음으로 교체:

```java
package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.presentation.dto.response.PolicyDetailResponse;
import com.youthfit.policy.presentation.dto.response.PolicyPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "정책", description = "정책 목록 조회, 상세 조회, 키워드 검색 API")
public interface PolicyApi {

    @Operation(summary = "정책 목록 조회",
            description = "필터 조건에 따라 정책 목록을 페이징 조회한다. status에 따라 정렬이 자동 결정된다 — OPEN: applyEnd asc, UPCOMING: applyStart asc, CLOSED: applyEnd desc.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<PolicyPageResponse> findPolicies(
            String regionCode,
            Category category,
            @Parameter(description = "정책 상태 필터: OPEN(진행중) / UPCOMING(예정) / CLOSED(마감). 미지정 시 전체.")
            PolicyStatus status,
            int page,
            int size);

    @Operation(summary = "정책 상세 조회", description = "정책 ID로 상세 정보를 조회한다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<PolicyDetailResponse> getPolicyDetail(
            @Parameter(description = "정책 ID") Long policyId);

    @Operation(summary = "정책 키워드 검색",
            description = "키워드로 정책을 검색한다. status를 함께 전달하면 동일한 정렬 규칙이 적용된다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "검색 성공"),
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<PolicyPageResponse> searchPolicies(
            String keyword,
            @Parameter(description = "정책 상태 필터: OPEN / UPCOMING / CLOSED. 미지정 시 전체.")
            PolicyStatus status,
            int page,
            int size);
}
```

- [ ] **Step 2: PolicyController 갱신**

`backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java` 전체를 다음으로 교체:

```java
package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.service.PolicyQueryService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.presentation.dto.response.PolicyDetailResponse;
import com.youthfit.policy.presentation.dto.response.PolicyPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController implements PolicyApi {

    private final PolicyQueryService policyQueryService;

    @GetMapping
    @Override
    public ResponseEntity<PolicyPageResponse> findPolicies(
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PolicyPageResult result = policyQueryService.findPoliciesByFilters(
                regionCode, category, status, page, size);
        return ResponseEntity.ok(PolicyPageResponse.from(result));
    }

    @GetMapping("/{policyId}")
    @Override
    public ResponseEntity<PolicyDetailResponse> getPolicyDetail(@PathVariable Long policyId) {
        PolicyDetailResult result = policyQueryService.findPolicyById(policyId);
        return ResponseEntity.ok(PolicyDetailResponse.from(result));
    }

    @GetMapping("/search")
    @Override
    public ResponseEntity<PolicyPageResponse> searchPolicies(
            @RequestParam String keyword,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PolicyPageResult result = policyQueryService.searchPoliciesByKeyword(keyword, status, page, size);
        return ResponseEntity.ok(PolicyPageResponse.from(result));
    }
}
```

- [ ] **Step 3: PolicyControllerTest 갱신**

`backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyControllerTest.java` 전체를 다음으로 교체:

```java
package com.youthfit.policy.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.application.service.PolicyQueryService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.PolicyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PolicyController")
@WebMvcTest(controllers = PolicyController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyQueryService policyQueryService;

    @Test
    @DisplayName("GET /api/v1/policies - 정책 목록을 조회한다")
    void findPolicies_returns200WithPage() throws Exception {
        // given
        PolicySummaryResult summary = new PolicySummaryResult(
                1L, "청년 취업 지원", "요약", Category.JOBS, "11",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30),
                2026, PolicyStatus.OPEN, DetailLevel.LITE, "서울시");
        PolicyPageResult pageResult = new PolicyPageResult(
                List.of(summary), 1L, 0, 20, 1, false);

        given(policyQueryService.findPoliciesByFilters(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/v1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("청년 취업 지원"))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/policies - 필터 파라미터(status 포함)를 전달할 수 있다")
    void findPolicies_withFilters_returns200() throws Exception {
        // given
        PolicyPageResult pageResult = new PolicyPageResult(List.of(), 0L, 0, 20, 0, false);
        given(policyQueryService.findPoliciesByFilters(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/v1/policies")
                        .param("regionCode", "11")
                        .param("category", "JOBS")
                        .param("status", "OPEN")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        then(policyQueryService).should()
                .findPoliciesByFilters(eq("11"), eq(Category.JOBS), eq(PolicyStatus.OPEN), eq(0), eq(10));
    }

    @Test
    @DisplayName("GET /api/v1/policies - sortType 파라미터는 더 이상 사용되지 않으며 무시된다")
    void findPolicies_legacySortTypeParam_isIgnored() throws Exception {
        // given
        PolicyPageResult pageResult = new PolicyPageResult(List.of(), 0L, 0, 20, 0, false);
        given(policyQueryService.findPoliciesByFilters(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then — sortType 파라미터가 있어도 200 응답
        mockMvc.perform(get("/api/v1/policies").param("sortType", "DEADLINE"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/policies/{policyId} - 정책 상세를 조회한다")
    void getPolicyDetail_returns200WithDetail() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 4, 15, 10, 0);
        PolicyDetailResult detail = new PolicyDetailResult(
                1L, "청년 취업 지원", "요약", null, null, null, null, null, null,
                Category.JOBS, "11",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30),
                null, null, null,
                PolicyStatus.OPEN, DetailLevel.LITE,
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                "https://youth.seoul.go.kr/policy/1",
                now, now);

        given(policyQueryService.findPolicyById(1L)).willReturn(detail);

        // when & then
        mockMvc.perform(get("/api/v1/policies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("청년 취업 지원"))
                .andExpect(jsonPath("$.category").value("JOBS"));
    }

    @Test
    @DisplayName("GET /api/v1/policies/search - 키워드만 전달하면 status는 null로 위임된다")
    void searchPolicies_keywordOnly_passesNullStatus() throws Exception {
        // given
        PolicySummaryResult summary = new PolicySummaryResult(
                1L, "청년 취업 지원", "요약", Category.JOBS, "11",
                null, null, 2026, PolicyStatus.OPEN, DetailLevel.LITE, "서울시");
        PolicyPageResult pageResult = new PolicyPageResult(
                List.of(summary), 1L, 0, 20, 1, false);

        given(policyQueryService.searchPoliciesByKeyword(eq("취업"), isNull(), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/v1/policies/search").param("keyword", "취업"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("청년 취업 지원"));
    }

    @Test
    @DisplayName("GET /api/v1/policies/search - status를 함께 전달하면 그대로 서비스에 위임된다")
    void searchPolicies_keywordWithStatus_passesStatus() throws Exception {
        // given
        PolicyPageResult pageResult = new PolicyPageResult(List.of(), 0L, 0, 20, 0, false);
        given(policyQueryService.searchPoliciesByKeyword(eq("취업"), eq(PolicyStatus.OPEN), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/v1/policies/search")
                        .param("keyword", "취업")
                        .param("status", "OPEN"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 4: 백엔드 전체 테스트 실행 + 빌드 확인**

Run: `cd backend && ./gradlew test build -x integrationTest`
Expected: BUILD SUCCESSFUL. 모든 정책 모듈 테스트 PASS.

만약 다른 모듈(예: ingestion)에서 `PolicySortType`을 import하고 있어 컴파일 실패가 나면 해당 import만 제거한다. 사용처 검색:

Run: `grep -rn "PolicySortType" backend/src --include="*.java"`
Expected: 이 시점에는 `PolicySortType.java` 외 사용처가 없어야 한다. 남은 사용처가 있으면 모두 제거한다.

- [ ] **Step 5: 커밋 — 백엔드 status 기반 정렬로 단일화**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/policy backend/src/test/java/com/youthfit/policy
git commit -m "$(cat <<'EOF'
refactor(policy): 정책 정렬을 status 인자 기반 분기로 단순화

PolicySpecification에서 sortType 인자와 statusWeight 가중치 정렬을 제거하고
status별 시간 축 기반 정렬(OPEN: applyEnd asc, UPCOMING: applyStart asc,
CLOSED: applyEnd desc)로 분기한다. 키워드 검색 API에도 status 파라미터를 추가해
탭 정렬을 동일하게 적용한다.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 백엔드 — PolicySortType enum 파일 삭제

**Files:**
- Delete: `backend/src/main/java/com/youthfit/policy/domain/model/PolicySortType.java`

- [ ] **Step 1: 사용처가 모두 제거되었는지 확인**

Run: `grep -rn "PolicySortType" backend/src --include="*.java"`
Expected: 출력 없음 (또는 `PolicySortType.java` 파일 자체만 노출).

만약 사용처가 남아 있다면 Task 1·2로 돌아가 마저 제거한다.

- [ ] **Step 2: 파일 삭제**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
rm backend/src/main/java/com/youthfit/policy/domain/model/PolicySortType.java
```

- [ ] **Step 3: 빌드 + 테스트 재확인**

Run: `cd backend && ./gradlew test build -x integrationTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/policy/domain/model/PolicySortType.java
git commit -m "$(cat <<'EOF'
refactor(policy): PolicySortType enum 제거

status 인자 기반 정렬로 단일화하면서 더 이상 사용되지 않는
PolicySortType enum을 제거한다.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 프론트엔드 — types/api/hook 정리

**Files:**
- Modify: `frontend/src/types/policy.ts`
- Modify: `frontend/src/apis/policy.api.ts`
- Modify: `frontend/src/hooks/queries/usePolicies.ts`

- [ ] **Step 1: types/policy.ts에서 PolicySortType 제거**

`frontend/src/types/policy.ts`에서 다음 라인을 삭제한다 (현재 12행):

```typescript
export type PolicySortType = 'LATEST' | 'DEADLINE' | 'UPCOMING';
```

다른 부분은 변경하지 않는다.

- [ ] **Step 2: apis/policy.api.ts 갱신**

`frontend/src/apis/policy.api.ts` 전체를 다음으로 교체:

```typescript
import api from './client';
import type { PolicyPage, PolicyDetail, PolicyStatus } from '@/types/policy';

interface PolicyListParams {
  category?: string;
  regionCode?: string;
  status?: string;
  page?: number;
  size?: number;
}

interface PolicySearchParams {
  status?: PolicyStatus;
  page?: number;
  size?: number;
}

export async function fetchPolicies(params: PolicyListParams): Promise<PolicyPage> {
  const searchParams = new URLSearchParams();
  if (params.category) searchParams.set('category', params.category);
  if (params.regionCode) searchParams.set('regionCode', params.regionCode);
  if (params.status) searchParams.set('status', params.status);
  searchParams.set('page', String(params.page ?? 0));
  searchParams.set('size', String(params.size ?? 20));

  return api.get('v1/policies', { searchParams }).json<PolicyPage>();
}

export async function searchPolicies(
  keyword: string,
  params: PolicySearchParams = {},
): Promise<PolicyPage> {
  const searchParams = new URLSearchParams();
  searchParams.set('keyword', keyword);
  if (params.status) searchParams.set('status', params.status);
  searchParams.set('page', String(params.page ?? 0));
  searchParams.set('size', String(params.size ?? 20));

  return api.get('v1/policies/search', { searchParams }).json<PolicyPage>();
}

export async function fetchPolicyDetail(policyId: number): Promise<PolicyDetail> {
  return api.get(`v1/policies/${policyId}`).json<PolicyDetail>();
}
```

- [ ] **Step 3: hooks/queries/usePolicies.ts 갱신**

`frontend/src/hooks/queries/usePolicies.ts` 전체를 다음으로 교체:

```typescript
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { fetchPolicies, searchPolicies } from '@/apis/policy.api';
import type { PolicyCategory, PolicyStatus } from '@/types/policy';

interface UsePoliciesParams {
  keyword?: string;
  category?: PolicyCategory | '';
  status?: PolicyStatus | '';
  regionCode?: string;
  page?: number;
  size?: number;
}

export function usePolicies(params: UsePoliciesParams) {
  const { keyword, category, status, regionCode, page = 0, size = 6 } = params;

  return useQuery({
    queryKey: ['policies', { keyword, category, status, regionCode, page, size }],
    queryFn: () =>
      keyword
        ? searchPolicies(keyword, { status: status || undefined, page, size })
        : fetchPolicies({
            category: category || undefined,
            status: status || undefined,
            regionCode: regionCode || undefined,
            page,
            size,
          }),
    placeholderData: keepPreviousData,
  });
}
```

- [ ] **Step 4: 타입 체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: 에러 없음. `PolicySortType` 또는 `sortType` 관련 에러가 나면 PolicyListPage.tsx에서 아직 import 중이라는 신호 — Task 5에서 정리한다.

- [ ] **Step 5: 진행 상황 검토 (커밋은 Task 5와 묶음)**

PolicyListPage.tsx까지 정리한 뒤 한 번에 커밋한다. 이 단계에서는 별도 git 작업 없음.

---

## Task 5: 프론트엔드 — PolicyListPage Tab Bar 도입

**Files:**
- Modify: `frontend/src/pages/PolicyListPage.tsx`

- [ ] **Step 1: import / 상수 정리**

`frontend/src/pages/PolicyListPage.tsx` 상단에서 다음을 제거한다.

11–15행 import 블록을 다음으로 교체:

```tsx
import type {
  PolicyCategory,
  PolicyStatus,
} from '@/types/policy';
```

(즉 `PolicySortType` import만 제거)

44–60행 `PAGE_SIZE` 직후의 정렬 상수·헬퍼 블록(`SORT_OPTIONS`, `DEFAULT_SORT`, `SORT_VALUES`, `parseSortType`)을 통째로 제거한다. `CATEGORY_ENTRIES`, `STATUS_ENTRIES`는 유지한다.

또한 `STATUS_ENTRIES` 바로 아래에 탭 노출 순서를 명시하기 위한 상수를 추가한다:

```tsx
const STATUS_TABS: { value: PolicyStatus; label: string }[] = [
  { value: 'OPEN', label: '진행중' },
  { value: 'UPCOMING', label: '예정' },
  { value: 'CLOSED', label: '마감' },
];

const DEFAULT_STATUS: PolicyStatus = 'OPEN';
```

- [ ] **Step 2: Tab Bar 컴포넌트 추가**

`Pagination` 컴포넌트 정의 직전(현재 188행 부근, `/* ── Pagination ── */` 주석 위)에 다음 컴포넌트를 추가한다:

```tsx
/* ──────────────────────────────────────────────
   StatusTabBar
   ────────────────────────────────────────────── */

function StatusTabBar({
  status,
  onStatusChange,
}: {
  status: PolicyStatus;
  onStatusChange: (next: PolicyStatus) => void;
}) {
  return (
    <div
      role="tablist"
      aria-label="정책 상태 필터"
      className="mb-4 flex w-full gap-1 overflow-x-auto rounded-2xl bg-gray-100 p-1"
    >
      {STATUS_TABS.map((tab) => {
        const active = status === tab.value;
        return (
          <button
            key={tab.value}
            role="tab"
            aria-selected={active}
            onClick={() => onStatusChange(tab.value)}
            className={cn(
              'min-h-11 flex-1 rounded-xl px-4 text-sm font-semibold transition-colors',
              active
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-500 hover:text-gray-900',
            )}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 3: status를 default OPEN으로 읽고 탭 콜백 정의**

현재 305행 부근:

```tsx
const status = (searchParams.get('status') ?? '') as PolicyStatus | '';
```

을 다음으로 교체:

```tsx
const rawStatus = searchParams.get('status');
const status: PolicyStatus = (
  rawStatus && ['OPEN', 'UPCOMING', 'CLOSED'].includes(rawStatus)
    ? rawStatus
    : DEFAULT_STATUS
) as PolicyStatus;
```

또한 307행 `const sortType = parseSortType(searchParams.get('sortType'));`를 **제거**한다.

`updateParams` 정의 아래(현재 337행 부근)에 탭 전환 콜백을 추가한다:

```tsx
const handleStatusTabChange = useCallback(
  (next: PolicyStatus) => {
    updateParams({ status: next, page: '' });
  },
  [updateParams],
);
```

- [ ] **Step 4: usePolicies 호출에서 sortType 제거**

311–319행을 다음으로 교체:

```tsx
const { data, isLoading, isError, refetch } = usePolicies({
  keyword: keyword || undefined,
  category,
  status,
  regionCode: regionCode || undefined,
  page,
  size: PAGE_SIZE,
});
```

(즉 `sortType` 라인 제거)

- [ ] **Step 5: 활성 필터 칩에서 status 항목 제거**

361–366행 `activeFilters` 구성 블록에서 다음 라인을 삭제한다:

```tsx
if (status) activeFilters.push({ key: 'status', label: STATUS_LABELS[status] });
```

`hasActiveQuery` 계산(현재 373행)도 status를 빼서 다음으로 교체:

```tsx
const hasActiveQuery = Boolean(keyword || category || regionCode);
```

- [ ] **Step 6: 데스크톱 영역의 status chip 제거 후 Tab Bar 삽입**

(1) 검색 폼(`<form role="search" …>…</form>`) **종료 태그 `</form>` 바로 다음 줄**에 Tab Bar를 추가한다 — 데스크톱·모바일 공통으로 페이지 상단에 항상 노출되도록 한다:

```tsx
<StatusTabBar status={status} onStatusChange={handleStatusTabChange} />
```

(2) 데스크톱 필터 블록(`<div className="mb-4 hidden flex-wrap items-center gap-2 md:flex">…</div>`) 내부에서, 카테고리 칩 직후의 divider(현재 `<span className="mx-1 h-6 w-px bg-neutral-200" aria-hidden="true" />` — 427행 부근)와 그 다음의 STATUS chip 매핑 블록(`{STATUS_ENTRIES.map(...)}` — 428–441행)을 통째로 제거한다. 카테고리 칩 다음에 곧바로 지역 select divider와 select가 이어지도록 한다.

- [ ] **Step 7: 모바일 필터 시트의 status 영역 제거**

`MobileFilterSheet` 컴포넌트(66–183행)에서:

- props 시그니처에서 `status`, `onStatusChange` 제거
- 137–155행의 `fieldset`(모집 상태 영역) 통째로 제거

또한 페이지 컴포넌트의 `<MobileFilterSheet ... />` 호출(현재 477–486행)에서 `status`와 `onStatusChange` 인자를 제거한다.

- [ ] **Step 8: sort dropdown UI 제거 — meta 영역 단순화**

509–535행 "Result meta + sort" 블록을 다음으로 교체:

```tsx
{/* ── Result meta ── */}
<div className="mb-4 flex items-center justify-between">
  <p className="text-sm text-gray-500">
    {data ? (
      <>
        <span className="font-semibold text-gray-900">{data.totalCount ?? 0}개</span> 정책
      </>
    ) : (
      <span>&nbsp;</span>
    )}
  </p>
</div>
```

- [ ] **Step 9: 미사용 import 정리**

상단 `STATUS_LABELS` import는 더 이상 사용되지 않으므로 16–20행 import 블록에서 다음으로 정리한다:

```tsx
import {
  CATEGORY_LABELS,
  REGION_OPTIONS,
} from '@/types/policy';
```

(즉 `STATUS_LABELS` 제거)

또한 `STATUS_ENTRIES`(60행 부근)도 더 이상 사용처가 없으므로 함께 제거한다.

- [ ] **Step 10: 타입 체크 + 빌드**

Run: `cd frontend && npx tsc --noEmit`
Expected: 에러 없음.

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: 개발 서버에서 수동 검증**

Run: `cd frontend && npm run dev`

브라우저에서 `http://localhost:5173/policies` 열고 다음을 차례로 확인:

- [ ] 페이지 진입 시 `진행중` 탭이 활성, OPEN 정책만 노출.
- [ ] `예정` 탭 클릭 시 URL이 `?status=UPCOMING`으로 갱신, 페이지 0으로 리셋.
- [ ] `마감` 탭 클릭 시 마감 정책이 마감일 최신 순으로 노출.
- [ ] 탭 + 카테고리 + 지역 + 검색어 조합 시 모두 정상 동작.
- [ ] 빈 탭(예: 예정 결과 0건) 시 빈 상태 메시지 노출.
- [ ] 브라우저 뒤로가기로 이전 탭 상태 복원.
- [ ] 활성 필터 칩에서 status 라벨이 사라진 것 확인 (카테고리·지역만 표시).
- [ ] 모바일 폭(개발자도구 ≤ 768px)에서 모바일 필터 시트를 열어도 모집 상태 영역이 없는 것 확인. 탭 바는 페이지 상단에 항상 노출.
- [ ] 외부 URL `?sortType=DEADLINE` 직접 접근 시 정상 동작 (무시되고 진행중 탭 활성).

체크리스트 모두 통과하면 다음 step으로 진행. 실패 항목이 있으면 해당 step으로 돌아가 수정.

- [ ] **Step 12: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add frontend/src/types/policy.ts frontend/src/apis/policy.api.ts frontend/src/hooks/queries/usePolicies.ts frontend/src/pages/PolicyListPage.tsx
git commit -m "$(cat <<'EOF'
feat(frontend): 정책 목록을 진행중·예정·마감 단일 선택 탭으로 개편

status 칩 토글을 페이지 상단 탭 바로 교체하고 default 활성 탭을 진행중(OPEN)으로
지정한다. PolicySortType과 사용자 정렬 dropdown을 제거하고, 키워드 검색에도
status를 함께 전달한다. 모바일 필터 시트의 모집 상태 영역과 활성 필터 칩에서의
status 항목도 제거해 중복 노출을 방지한다.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: 최종 점검

- [ ] **Step 1: 백엔드·프론트엔드 빌드 동시 통과 확인**

Run: `cd backend && ./gradlew test build -x integrationTest`
Expected: BUILD SUCCESSFUL.

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: 두 명령 모두 성공.

- [ ] **Step 2: 잔존 사용처 최종 확인**

Run: `grep -rn "PolicySortType\|sortType" backend/src frontend/src 2>/dev/null`
Expected: 출력 없음.

- [ ] **Step 3: PR 생성 (선택)**

사용자가 PR 생성을 요청하면 `/create-pr` 스킬 또는 다음 명령으로:

```bash
gh pr create --title "feat(policy): 정책 목록 상태 탭 개편" --body "$(cat <<'EOF'
## Summary
- 정책 정렬을 status 인자 기반 분기로 단순화 (PolicySortType enum 제거)
- 정책 목록 페이지를 진행중·예정·마감 단일 선택 탭으로 개편
- 키워드 검색 API에 status 파라미터 추가

## Design
docs/superpowers/specs/2026-04-27-policy-list-status-tabs-design.md

## Test plan
- [x] 백엔드 단위 테스트 (PolicySpecificationTest, PolicyQueryServiceTest, PolicyControllerTest) PASS
- [x] 프론트엔드 타입 체크 + 빌드 통과
- [x] 수동 검증 체크리스트 (탭 전환, 빈 탭, 뒤로가기, 모바일, legacy URL) 통과

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
