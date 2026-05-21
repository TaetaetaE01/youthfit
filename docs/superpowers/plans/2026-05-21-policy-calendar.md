# 정책 달력 (Policy Calendar) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/policies/calendar` 페이지를 신설해 정책 신청 기간을 월 그리드(데스크톱)·아젠다 리스트(모바일) 형태로 시각화하고, 상시 정책은 별도 섹션으로 노출한다.

**Architecture:** 백엔드는 `GET /api/v1/policies/calendar` 신규 엔드포인트(기간 overlap 의미론 + 경량 DTO)와 `GET /api/v1/policies/calendar/always-open` 두 가지를 추가한다. 프론트는 순수 함수 `calendarLayout.ts` 가 막대 레이아웃을 계산하고 `CalendarMonthGrid`/`CalendarAgenda` 두 뷰가 동일 데이터를 다르게 그린다. 카테고리/지역 필터는 `PolicyListPage` 에서 `PolicyFilterBar` 로 추출해 공유한다.

**Tech Stack:** Spring Boot 4.x · JPA Specification · React 19 · TanStack Query v5 · Tailwind CSS v4 · shadcn/ui · React Router v7 · Vitest

**Spec reference:** `docs/superpowers/specs/2026-05-21-policy-calendar-design.md`

---

## File Structure

**Backend (new)**
- `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyCalendarResult.java`
- `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyCalendarPageResult.java`
- `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyCalendarResponse.java`
- `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyCalendarPageResponse.java`

**Backend (modify)**
- `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java` — `withCalendarRange`, `alwaysOpen` 추가
- `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java` — `findByCalendarRange`, `findAlwaysOpen` 시그니처 추가
- `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java` — 구현 추가
- `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java` — `findByDateRange`, `findAlwaysOpen` 추가
- `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java` — `/calendar`, `/calendar/always-open` 매핑

**Frontend (new)**
- `frontend/src/lib/calendarLayout.ts`
- `frontend/src/lib/__tests__/calendarLayout.test.ts`
- `frontend/src/hooks/queries/usePolicyCalendar.ts`
- `frontend/src/hooks/queries/useAlwaysOpenPolicies.ts`
- `frontend/src/components/policy/PolicyFilterBar.tsx`
- `frontend/src/components/policy-calendar/CalendarBar.tsx`
- `frontend/src/components/policy-calendar/CalendarMonthGrid.tsx`
- `frontend/src/components/policy-calendar/CalendarAgenda.tsx`
- `frontend/src/components/policy-calendar/CalendarDayPopover.tsx`
- `frontend/src/components/policy-calendar/AlwaysOpenSection.tsx`
- `frontend/src/components/policy-calendar/CalendarHeader.tsx`
- `frontend/src/pages/PolicyCalendarPage.tsx`

**Frontend (modify)**
- `frontend/src/types/policy.ts` — `PolicyCalendarItem` 추가
- `frontend/src/apis/policy.api.ts` — `fetchCalendarPolicies`, `fetchAlwaysOpenPolicies` 추가
- `frontend/src/pages/PolicyListPage.tsx` — `PolicyFilterBar` 사용으로 리팩토링
- `frontend/src/components/layout/Navbar.tsx` — `NAV_LINKS` 에 "정책 달력" 추가
- `frontend/src/App.tsx` — `/policies/calendar` 라우트 등록

---

## Phase 1 — Backend

### Task B1: PolicyCalendarResult / Response DTO

DTO 만 추가하는 작업. 응답에 필요한 경량 필드만 정의한다.

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyCalendarResult.java`
- Create: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyCalendarPageResult.java`
- Create: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyCalendarResponse.java`
- Create: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyCalendarPageResponse.java`

- [ ] **Step 1: Create `PolicyCalendarResult` record**

```java
package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;

import java.time.LocalDate;

public record PolicyCalendarResult(
        Long id,
        String title,
        Category category,
        LocalDate applyStart,
        LocalDate applyEnd,
        String regionLabel
) {
    public static PolicyCalendarResult from(Policy policy) {
        return new PolicyCalendarResult(
                policy.getId(),
                policy.getTitle(),
                policy.getCategory(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getRegionCode()
        );
    }
}
```

- [ ] **Step 2: Create `PolicyCalendarPageResult` record (상시 정책용 페이지)**

```java
package com.youthfit.policy.application.dto.result;

import java.util.List;

public record PolicyCalendarPageResult(
        List<PolicyCalendarResult> items,
        long totalCount,
        int page,
        int size,
        int totalPages,
        boolean hasNext
) { }
```

- [ ] **Step 3: Create `PolicyCalendarResponse` record**

```java
package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyCalendarResult;
import com.youthfit.policy.domain.model.Category;

import java.time.LocalDate;

public record PolicyCalendarResponse(
        Long id,
        String title,
        Category category,
        LocalDate applyStart,
        LocalDate applyEnd,
        String regionLabel
) {
    public static PolicyCalendarResponse from(PolicyCalendarResult result) {
        return new PolicyCalendarResponse(
                result.id(),
                result.title(),
                result.category(),
                result.applyStart(),
                result.applyEnd(),
                result.regionLabel()
        );
    }
}
```

- [ ] **Step 4: Create `PolicyCalendarPageResponse` record**

```java
package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyCalendarPageResult;

import java.util.List;

public record PolicyCalendarPageResponse(
        List<PolicyCalendarResponse> content,
        long totalCount,
        int page,
        int size,
        int totalPages,
        boolean hasNext
) {
    public static PolicyCalendarPageResponse from(PolicyCalendarPageResult result) {
        return new PolicyCalendarPageResponse(
                result.items().stream().map(PolicyCalendarResponse::from).toList(),
                result.totalCount(),
                result.page(),
                result.size(),
                result.totalPages(),
                result.hasNext()
        );
    }
}
```

- [ ] **Step 5: Compile**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyCalendarResult.java \
        backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyCalendarPageResult.java \
        backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyCalendarResponse.java \
        backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyCalendarPageResponse.java
git commit -m "feat(policy): 정책 달력 DTO (PolicyCalendarResult/Response)"
```

---

### Task B2: PolicySpecification — overlap, alwaysOpen 추가 (TDD)

`applyStart <= to AND applyEnd >= from` 의미론을 가진 Specification 을 추가한다. `null` 경계 케이스가 까다로워서 슬라이스 테스트로 검증한다.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java`
- Create: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationCalendarTest.java`

- [ ] **Step 1: Create failing slice test**

먼저 슬라이스 테스트 파일을 만든다. `@DataJpaTest` 로 H2 in-memory DB 사용.

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.RegionFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
class PolicySpecificationCalendarTest {

    @Autowired
    private PolicyJpaRepository repository;

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("조회 범위에 완전히 포함된 정책")
    void fullyInsideRange() {
        Policy p = policyWithDates("내부", LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 20));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1).extracting(Policy::getTitle).containsExactly("내부");
    }

    @Test
    @DisplayName("조회 범위 좌측에 걸친 정책 (시작이 from 이전, 마감이 범위 안)")
    void straddlesLeft() {
        Policy p = policyWithDates("좌측", LocalDate.of(2026, 2, 20), LocalDate.of(2026, 3, 10));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("조회 범위 우측에 걸친 정책 (시작이 범위 안, 마감이 to 이후)")
    void straddlesRight() {
        Policy p = policyWithDates("우측", LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 10));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("조회 범위를 완전히 포함하는 정책")
    void containsRange() {
        Policy p = policyWithDates("포함", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("범위 밖 정책은 제외")
    void outsideRange() {
        repository.save(policyWithDates("이전", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        repository.save(policyWithDates("이후", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)));

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("applyStart=null, applyEnd>=from 이면 포함 (마감만 있는 경우)")
    void nullStartButEndInsideRange() {
        Policy p = policyWithDates("시작null", null, LocalDate.of(2026, 3, 15));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("applyEnd=null, applyStart<=to 이면 포함 (시작만 있는 경우)")
    void nullEndButStartInsideRange() {
        Policy p = policyWithDates("끝null", LocalDate.of(2026, 3, 15), null);
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("applyStart=null AND applyEnd=null (상시 정책) 은 calendar 에서 제외")
    void bothNullExcluded() {
        Policy p = policyWithDates("상시", null, null);
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("alwaysOpen Specification 은 applyStart=null AND applyEnd=null 만 반환")
    void alwaysOpenReturnsOnlyBothNull() {
        repository.save(policyWithDates("상시1", null, null));
        repository.save(policyWithDates("상시2", null, null));
        repository.save(policyWithDates("기간있음", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        repository.save(policyWithDates("끝만있음", null, LocalDate.of(2026, 3, 31)));

        List<Policy> result = repository.findAll(
                PolicySpecification.alwaysOpen(RegionFilter.of(null), null));

        assertThat(result).hasSize(2)
                .extracting(Policy::getTitle)
                .containsExactlyInAnyOrder("상시1", "상시2");
    }

    private Policy policyWithDates(String title, LocalDate start, LocalDate end) {
        return Policy.builder()
                .title(title)
                .normalizedTitle(title)
                .summary("test")
                .category(Category.HOUSING)
                .regionCode("전국")
                .applyStart(start)
                .applyEnd(end)
                .build();
    }
}
```

> Note: `Policy.builder()` 의 정확한 필드/필수값은 기존 `Policy.java` 와 `PolicyTest.java` 를 참고. 위 예시는 호출 패턴이고, 필수 필드가 더 있으면 추가한다.

- [ ] **Step 2: Run test to confirm it fails (compile error: `withCalendarRange` / `alwaysOpen` not defined)**

Run: `cd backend && ./gradlew test --tests PolicySpecificationCalendarTest`
Expected: COMPILE FAIL — `cannot find symbol withCalendarRange`

- [ ] **Step 3: Add `withCalendarRange` and `alwaysOpen` to `PolicySpecification.java`**

기존 `PolicySpecification.java` 의 `withFilters` 메서드 *바로 아래* 에 추가한다. `applyOrder` 헬퍼와 `regionPredicate` 헬퍼는 기존 것을 재사용한다.

```java
public static Specification<Policy> withCalendarRange(LocalDate from, LocalDate to,
                                                      RegionFilter regionFilter,
                                                      Category category) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        Path<LocalDate> applyStart = root.get("applyStart");
        Path<LocalDate> applyEnd = root.get("applyEnd");

        // applyStart <= to (null 이면 만족으로 간주)
        Predicate startOk = cb.or(
                cb.isNull(applyStart),
                cb.lessThanOrEqualTo(applyStart, to)
        );
        // applyEnd >= from (null 이면 만족으로 간주)
        Predicate endOk = cb.or(
                cb.isNull(applyEnd),
                cb.greaterThanOrEqualTo(applyEnd, from)
        );
        predicates.add(startOk);
        predicates.add(endOk);

        // 둘 다 null 인 상시 정책은 제외
        predicates.add(cb.or(
                cb.isNotNull(applyStart),
                cb.isNotNull(applyEnd)
        ));

        // applyStart > applyEnd 인 데이터 오류 제외
        predicates.add(cb.or(
                cb.isNull(applyStart),
                cb.isNull(applyEnd),
                cb.lessThanOrEqualTo(applyStart, applyEnd)
        ));

        if (regionFilter != null && regionFilter.isActive()) {
            predicates.add(regionPredicate(root, cb, regionFilter));
        }
        if (category != null) {
            predicates.add(cb.equal(root.get("category"), category));
        }

        // 정렬: applyStart 오름차순 (null 은 뒤로), tie 면 applyEnd 내림차순
        if (query != null) {
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                query.orderBy(
                        cb.asc(cb.coalesce(applyStart, LocalDate.of(9999, 12, 31))),
                        cb.desc(cb.coalesce(applyEnd, LocalDate.of(1, 1, 1)))
                );
            }
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    };
}

public static Specification<Policy> alwaysOpen(RegionFilter regionFilter, Category category) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.isNull(root.get("applyStart")));
        predicates.add(cb.isNull(root.get("applyEnd")));

        if (regionFilter != null && regionFilter.isActive()) {
            predicates.add(regionPredicate(root, cb, regionFilter));
        }
        if (category != null) {
            predicates.add(cb.equal(root.get("category"), category));
        }

        if (query != null) {
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    };
}
```

- [ ] **Step 4: Run test to confirm it passes**

Run: `cd backend && ./gradlew test --tests PolicySpecificationCalendarTest`
Expected: BUILD SUCCESSFUL · 9 tests passed

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationCalendarTest.java
git commit -m "feat(policy): PolicySpecification overlap·alwaysOpen 추가 + 슬라이스 테스트"
```

---

### Task B3: PolicyRepository port + impl 확장

도메인 포트와 어댑터에 `findByCalendarRange`, `findAlwaysOpen` 메서드를 추가한다.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java`

- [ ] **Step 1: Add methods to `PolicyRepository` port interface**

`backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java` 에 import 추가:

```java
import java.time.LocalDate;
```

그리고 인터페이스 안에 다음 메서드 시그니처를 추가:

```java
List<Policy> findByCalendarRange(LocalDate from, LocalDate to,
                                  RegionFilter regionFilter, Category category);

Page<Policy> findAlwaysOpen(RegionFilter regionFilter, Category category, Pageable pageable);
```

- [ ] **Step 2: Implement in `PolicyRepositoryImpl`**

`PolicyRepositoryImpl.java` 에 import 추가:

```java
import java.time.LocalDate;
```

클래스 안에 메서드 추가:

```java
@Override
public List<Policy> findByCalendarRange(LocalDate from, LocalDate to,
                                         RegionFilter regionFilter, Category category) {
    return jpaRepository.findAll(
            PolicySpecification.withCalendarRange(from, to, regionFilter, category));
}

@Override
public Page<Policy> findAlwaysOpen(RegionFilter regionFilter, Category category, Pageable pageable) {
    return jpaRepository.findAll(
            PolicySpecification.alwaysOpen(regionFilter, category), pageable);
}
```

- [ ] **Step 3: Compile**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run existing tests (no regression)**

Run: `cd backend && ./gradlew test --tests "*policy*"`
Expected: All policy tests pass

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java
git commit -m "feat(policy): PolicyRepository 에 findByCalendarRange·findAlwaysOpen 추가"
```

---

### Task B4: PolicyQueryService — findByDateRange / findAlwaysOpen (TDD)

서비스 레이어에 두 조회 메서드 추가. 범위 검증(`from <= to`, 92일 제한, ±24개월 한계)은 이 레이어에서 한다.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java`

- [ ] **Step 1: Add failing tests to `PolicyQueryServiceTest.java`**

기존 테스트 파일 끝쪽에 새 `@Nested` 클래스 또는 메서드들을 추가한다. 기존 모킹 구조를 그대로 따른다 (현 파일 참조).

```java
@Test
@DisplayName("findByDateRange — 정상 호출: PolicyRepository.findByCalendarRange 결과를 결과 DTO 로 매핑")
void findByDateRange_success() {
    LocalDate from = LocalDate.of(2026, 3, 1);
    LocalDate to = LocalDate.of(2026, 3, 31);
    Policy p = Policy.builder()
            .id(1L).title("청년월세").normalizedTitle("청년월세").summary("test")
            .category(Category.HOUSING).regionCode("전국")
            .applyStart(LocalDate.of(2026, 3, 10))
            .applyEnd(LocalDate.of(2026, 3, 20))
            .build();
    when(policyRepository.findByCalendarRange(from, to, any(), eq(null)))
            .thenReturn(List.of(p));

    List<PolicyCalendarResult> result =
            policyQueryService.findByDateRange(from, to, RegionFilter.of(null), null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(1L);
    assertThat(result.get(0).applyStart()).isEqualTo(LocalDate.of(2026, 3, 10));
}

@Test
@DisplayName("findByDateRange — from > to 면 IllegalArgumentException")
void findByDateRange_invalidOrder() {
    LocalDate from = LocalDate.of(2026, 3, 31);
    LocalDate to = LocalDate.of(2026, 3, 1);

    assertThatThrownBy(() ->
            policyQueryService.findByDateRange(from, to, RegionFilter.of(null), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("from");
}

@Test
@DisplayName("findByDateRange — 범위가 92일을 초과하면 IllegalArgumentException")
void findByDateRange_rangeTooLarge() {
    LocalDate from = LocalDate.of(2026, 3, 1);
    LocalDate to = LocalDate.of(2026, 6, 30);  // 121일

    assertThatThrownBy(() ->
            policyQueryService.findByDateRange(from, to, RegionFilter.of(null), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("범위");
}

@Test
@DisplayName("findAlwaysOpen — 페이지 결과를 PolicyCalendarPageResult 로 매핑")
void findAlwaysOpen_success() {
    Policy p = Policy.builder()
            .id(2L).title("상시멘토링").normalizedTitle("상시멘토링").summary("test")
            .category(Category.EDUCATION).regionCode("전국")
            .build();
    Page<Policy> page = new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1);
    when(policyRepository.findAlwaysOpen(any(), eq(null), any(Pageable.class)))
            .thenReturn(page);

    PolicyCalendarPageResult result =
            policyQueryService.findAlwaysOpen(RegionFilter.of(null), null, 0, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).id()).isEqualTo(2L);
    assertThat(result.totalCount()).isEqualTo(1L);
}
```

> 필요한 import: `java.time.LocalDate`, `org.springframework.data.domain.PageImpl`, `org.springframework.data.domain.Pageable`, `org.springframework.data.domain.PageRequest`, `static org.assertj.core.api.Assertions.assertThatThrownBy`, `com.youthfit.policy.application.dto.result.PolicyCalendarResult`, `com.youthfit.policy.application.dto.result.PolicyCalendarPageResult`.

- [ ] **Step 2: Run tests to confirm they fail**

Run: `cd backend && ./gradlew test --tests PolicyQueryServiceTest`
Expected: 4 failures (`findByDateRange`/`findAlwaysOpen` not defined)

- [ ] **Step 3: Implement service methods**

`PolicyQueryService.java` 에 import 추가:

```java
import com.youthfit.policy.application.dto.result.PolicyCalendarResult;
import com.youthfit.policy.application.dto.result.PolicyCalendarPageResult;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
```

클래스 안에 상수 추가 (기존 `NATIONWIDE_CODE_THRESHOLD` 옆):

```java
private static final long CALENDAR_RANGE_MAX_DAYS = 92;
```

클래스 안에 두 메서드 추가:

```java
public List<PolicyCalendarResult> findByDateRange(LocalDate from, LocalDate to,
                                                   RegionFilter regionFilter,
                                                   Category category) {
    if (from == null || to == null) {
        throw new IllegalArgumentException("from, to 는 필수입니다");
    }
    if (from.isAfter(to)) {
        throw new IllegalArgumentException("from 은 to 보다 이전이거나 같아야 합니다");
    }
    long days = ChronoUnit.DAYS.between(from, to);
    if (days > CALENDAR_RANGE_MAX_DAYS) {
        throw new IllegalArgumentException(
                "조회 범위는 " + CALENDAR_RANGE_MAX_DAYS + "일을 초과할 수 없습니다");
    }

    return policyRepository.findByCalendarRange(from, to, regionFilter, category)
            .stream()
            .map(PolicyCalendarResult::from)
            .toList();
}

public PolicyCalendarPageResult findAlwaysOpen(RegionFilter regionFilter,
                                                Category category,
                                                int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<Policy> policyPage = policyRepository.findAlwaysOpen(regionFilter, category, pageable);

    List<PolicyCalendarResult> items = policyPage.getContent().stream()
            .map(PolicyCalendarResult::from)
            .toList();

    return new PolicyCalendarPageResult(
            items,
            policyPage.getTotalElements(),
            policyPage.getNumber(),
            policyPage.getSize(),
            policyPage.getTotalPages(),
            policyPage.hasNext()
    );
}
```

- [ ] **Step 4: Run tests to confirm they pass**

Run: `cd backend && ./gradlew test --tests PolicyQueryServiceTest`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java \
        backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java
git commit -m "feat(policy): PolicyQueryService.findByDateRange·findAlwaysOpen + 검증 로직"
```

---

### Task B5: PolicyController — /calendar 엔드포인트 (TDD)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java`
- Create: `backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyCalendarControllerTest.java`

- [ ] **Step 1: Create failing MockMvc test**

기존 컨트롤러 테스트 패턴을 따른다. 프로젝트에 이미 컨트롤러 테스트가 있다면 동일 구성을 복제.

```java
package com.youthfit.policy.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.policy.application.dto.result.PolicyCalendarPageResult;
import com.youthfit.policy.application.dto.result.PolicyCalendarResult;
import com.youthfit.policy.application.service.PolicyQueryService;
import com.youthfit.policy.domain.model.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
class PolicyCalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PolicyQueryService policyQueryService;

    @Test
    @DisplayName("GET /api/v1/policies/calendar — 200 + items 반환")
    void calendar_ok() throws Exception {
        PolicyCalendarResult r = new PolicyCalendarResult(
                1L, "청년월세", Category.HOUSING,
                LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 31), "전국");
        when(policyQueryService.findByDateRange(any(), any(), any(), eq(null)))
                .thenReturn(List.of(r));

        mockMvc.perform(get("/api/v1/policies/calendar")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].title").value("청년월세"))
                .andExpect(jsonPath("$.items[0].applyStart").value("2026-03-14"));
    }

    @Test
    @DisplayName("GET /api/v1/policies/calendar — from > to 면 400")
    void calendar_invalidRange() throws Exception {
        when(policyQueryService.findByDateRange(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("from"));

        mockMvc.perform(get("/api/v1/policies/calendar")
                        .param("from", "2026-03-31")
                        .param("to", "2026-03-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/policies/calendar/always-open — 200 + 페이지 응답")
    void alwaysOpen_ok() throws Exception {
        PolicyCalendarResult r = new PolicyCalendarResult(
                2L, "상시멘토링", Category.EDUCATION, null, null, "전국");
        PolicyCalendarPageResult page = new PolicyCalendarPageResult(
                List.of(r), 1L, 0, 20, 1, false);
        when(policyQueryService.findAlwaysOpen(any(), eq(null), eq(0), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/policies/calendar/always-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.totalCount").value(1));
    }
}
```

> 프로젝트가 `IllegalArgumentException` 을 별도 핸들러(`@RestControllerAdvice`)로 400 으로 매핑하고 있다면 그대로 통과한다. 매핑이 없다면 `PolicyController` 에 로컬 `@ExceptionHandler` 를 추가해야 한다 — 다음 Step 에서 확인한다.

- [ ] **Step 2: Run test to confirm it fails**

Run: `cd backend && ./gradlew test --tests PolicyCalendarControllerTest`
Expected: FAIL — 404 (엔드포인트 미존재) 또는 컴파일 에러

- [ ] **Step 3: Add endpoints to `PolicyController.java`**

import 추가:

```java
import com.youthfit.policy.application.dto.result.PolicyCalendarPageResult;
import com.youthfit.policy.application.dto.result.PolicyCalendarResult;
import com.youthfit.policy.presentation.dto.response.PolicyCalendarPageResponse;
import com.youthfit.policy.presentation.dto.response.PolicyCalendarResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
```

엔드포인트 두 개 추가:

```java
@GetMapping("/calendar")
public ResponseEntity<CalendarResponseWrapper> findCalendarPolicies(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String regions,
        @RequestParam(required = false) String regionCode,
        @RequestParam(required = false) Category category) {

    RegionFilter filter = resolveRegionFilter(regions, regionCode);
    List<PolicyCalendarResult> results =
            policyQueryService.findByDateRange(from, to, filter, category);
    List<PolicyCalendarResponse> items = results.stream()
            .map(PolicyCalendarResponse::from)
            .toList();
    return ResponseEntity.ok(new CalendarResponseWrapper(items));
}

@GetMapping("/calendar/always-open")
public ResponseEntity<PolicyCalendarPageResponse> findAlwaysOpenPolicies(
        @RequestParam(required = false) String regions,
        @RequestParam(required = false) String regionCode,
        @RequestParam(required = false) Category category,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

    RegionFilter filter = resolveRegionFilter(regions, regionCode);
    PolicyCalendarPageResult result =
            policyQueryService.findAlwaysOpen(filter, category, page, Math.min(size, 50));
    return ResponseEntity.ok(PolicyCalendarPageResponse.from(result));
}

@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<Map<String, String>> handleInvalidArg(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", e.getMessage()));
}

public record CalendarResponseWrapper(List<PolicyCalendarResponse> items) {}
```

> 만약 전역 `@RestControllerAdvice` 가 이미 `IllegalArgumentException` 을 400 으로 매핑한다면 위 `@ExceptionHandler` 는 *생략*. `backend/src/main/java/com/youthfit/common/exception/` 디렉토리를 확인.

- [ ] **Step 4: Verify global exception handling**

Check: `ls backend/src/main/java/com/youthfit/common/exception/` 에 `GlobalExceptionHandler` 가 있고 `IllegalArgumentException` 을 처리하는지 확인. 처리한다면 위 로컬 `@ExceptionHandler` 와 `Map<String,String>`/`HttpStatus` import 는 제거한다.

- [ ] **Step 5: Run test to confirm it passes**

Run: `cd backend && ./gradlew test --tests PolicyCalendarControllerTest`
Expected: BUILD SUCCESSFUL · 3 tests passed

- [ ] **Step 6: Smoke test the endpoint (dev server)**

```bash
cd backend && ./gradlew bootRun &
# wait for startup
curl -s "http://localhost:8080/api/v1/policies/calendar?from=2026-05-01&to=2026-05-31" | jq '.items | length'
curl -s "http://localhost:8080/api/v1/policies/calendar/always-open?size=5" | jq '.totalCount'
```

Expected: 두 호출 모두 정상 JSON 반환. 종료: `kill %1`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java \
        backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyCalendarControllerTest.java
git commit -m "feat(policy): GET /api/v1/policies/calendar·/calendar/always-open 엔드포인트"
```

---

## Phase 2 — Frontend Foundation

### Task F1: PolicyCalendarItem 타입 추가

**Files:**
- Modify: `frontend/src/types/policy.ts`

- [ ] **Step 1: Add `PolicyCalendarItem` and response wrapper types**

`frontend/src/types/policy.ts` 의 파일 끝에 추가:

```typescript
export type PolicyCalendarItem = {
  id: number;
  title: string;
  category: PolicyCategory;
  applyStart: string | null;   // YYYY-MM-DD
  applyEnd: string | null;     // YYYY-MM-DD
  regionLabel: string;
};

export type PolicyCalendarResponse = {
  items: PolicyCalendarItem[];
};

export type PolicyCalendarPageResponse = {
  content: PolicyCalendarItem[];
  totalCount: number;
  page: number;
  size: number;
  totalPages: number;
  hasNext: boolean;
};
```

> `PolicyCategory` 가 이미 export 되어 있어야 한다. 기존 `types/policy.ts` 에 정의되어 있다면 그대로 사용.

- [ ] **Step 2: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/policy.ts
git commit -m "feat(policy): PolicyCalendarItem 타입 추가"
```

---

### Task F2: calendarLayout 순수 함수 + 단위 테스트 (TDD)

월 그리드 배치 알고리즘. UI 없이 먼저 검증.

**Files:**
- Create: `frontend/src/lib/calendarLayout.ts`
- Create: `frontend/src/lib/__tests__/calendarLayout.test.ts`

- [ ] **Step 1: Write failing tests**

```typescript
import { describe, it, expect } from 'vitest';
import { layoutBars } from '../calendarLayout';
import type { PolicyCalendarItem } from '@/types/policy';

const mk = (
  id: number,
  applyStart: string | null,
  applyEnd: string | null,
  title = `policy-${id}`,
): PolicyCalendarItem => ({
  id,
  title,
  category: 'HOUSING' as any,
  applyStart,
  applyEnd,
  regionLabel: '전국',
});

describe('layoutBars', () => {
  // 2026-03: 일=3/1, 첫 주 = 3/1(일)~3/7(토), 둘째 = 3/8~3/14, ...
  const monthStart = '2026-03-01';
  const monthEnd = '2026-03-31';

  it('한 주 안에서 완결되는 막대 1개 — 정확히 1 세그먼트', () => {
    const items = [mk(1, '2026-03-10', '2026-03-12')];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    expect(segments).toHaveLength(1);
    expect(segments[0]).toMatchObject({
      itemId: 1,
      weekIndex: 1,        // 둘째 주 (0-base)
      startCol: 2,         // 화요일 (일=0)
      endCol: 4,           // 목요일
      hasStartCap: true,
      hasEndCap: true,
      row: 0,
    });
  });

  it('시간상 겹치는 두 막대 — 다른 행에 배치', () => {
    const items = [
      mk(1, '2026-03-10', '2026-03-14'),
      mk(2, '2026-03-12', '2026-03-16'),
    ];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    const rows = segments.map((s) => s.row);
    expect(new Set(rows).size).toBeGreaterThan(1);
  });

  it('안 겹치는 두 막대 — 같은 행 재사용', () => {
    const items = [
      mk(1, '2026-03-10', '2026-03-11'),
      mk(2, '2026-03-13', '2026-03-14'),
    ];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    // 두 막대 모두 같은 주에 들어가고, 안 겹치면 row 0 재사용 가능
    expect(segments[0].row).toBe(0);
    expect(segments[1].row).toBe(0);
  });

  it('주 경계 분할 — 좌측 끝주 우측 캡 없음, 우측 끝주 좌측 캡 없음', () => {
    // 3/5(목)~3/12(목) 은 첫 주 + 둘째 주 두 세그먼트
    const items = [mk(1, '2026-03-05', '2026-03-12')];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    expect(segments).toHaveLength(2);

    const first = segments.find((s) => s.weekIndex === 0)!;
    const second = segments.find((s) => s.weekIndex === 1)!;

    expect(first.hasStartCap).toBe(true);
    expect(first.hasEndCap).toBe(false);   // 다음 주로 이어짐
    expect(second.hasStartCap).toBe(false); // 지난 주에서 이어옴
    expect(second.hasEndCap).toBe(true);
  });

  it('셀당 3행 초과 시 overflowByDay 카운트 반환', () => {
    // 3/10 에 5개 정책이 동시에 시작 (시간상 모두 겹침)
    const items = [
      mk(1, '2026-03-10', '2026-03-12'),
      mk(2, '2026-03-10', '2026-03-12'),
      mk(3, '2026-03-10', '2026-03-12'),
      mk(4, '2026-03-10', '2026-03-12'),
      mk(5, '2026-03-10', '2026-03-12'),
    ];
    const { segments, overflowByDay } = layoutBars(items, monthStart, monthEnd);

    // row 0,1,2 까지만 배치되고 row 3,4 는 overflow
    expect(segments.filter((s) => !s.isOverflow)).toHaveLength(3);
    // 3/10, 3/11, 3/12 각각 +2
    expect(overflowByDay['2026-03-10']).toBe(2);
    expect(overflowByDay['2026-03-11']).toBe(2);
    expect(overflowByDay['2026-03-12']).toBe(2);
  });

  it('applyStart=null 케이스 — 좌측 캡 없는 막대로 처리, 첫 주부터 시작', () => {
    const items = [mk(1, null, '2026-03-10')];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    expect(segments.length).toBeGreaterThan(0);
    const first = segments[0];
    expect(first.hasStartCap).toBe(false);
    expect(first.weekIndex).toBe(0); // 첫 주부터
  });

  it('applyEnd=null 케이스 — 우측 캡 없는 막대로 처리, 마지막 주까지', () => {
    const items = [mk(1, '2026-03-25', null)];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    expect(segments.length).toBeGreaterThan(0);
    const last = segments[segments.length - 1];
    expect(last.hasEndCap).toBe(false);
    // 3월 마지막 주까지 이어짐
  });

  it('정렬 — applyStart 오름차순, tie 면 더 긴 정책 먼저', () => {
    const items = [
      mk(1, '2026-03-10', '2026-03-12', 'short'),
      mk(2, '2026-03-10', '2026-03-20', 'long'),
    ];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    // 더 긴 정책 (id=2) 이 먼저 (낮은 row)
    const s1 = segments.find((s) => s.itemId === 1)!;
    const s2 = segments.find((s) => s.itemId === 2)!;
    expect(s2.row).toBeLessThanOrEqualTo(s1.row);
  });
});
```

> Vitest 의 `toBeLessThanOrEqualTo` 가 없다면 `toBeLessThanOrEqual` 로. 프로젝트 vitest 버전에 따라 다름.

- [ ] **Step 2: Run tests to confirm they fail**

Run: `cd frontend && npm run test -- src/lib/__tests__/calendarLayout.test.ts`
Expected: COMPILE FAIL — `layoutBars` not exported

- [ ] **Step 3: Implement `calendarLayout.ts`**

```typescript
import type { PolicyCalendarItem } from '@/types/policy';

export type BarSegment = {
  itemId: number;
  title: string;
  category: string;
  weekIndex: number;
  startCol: number;     // 0=Sun ~ 6=Sat (inclusive)
  endCol: number;
  hasStartCap: boolean;
  hasEndCap: boolean;
  row: number;
  isOverflow: boolean;
  applyStart: string | null;
  applyEnd: string | null;
};

export type LayoutResult = {
  segments: BarSegment[];
  overflowByDay: Record<string, number>; // 'YYYY-MM-DD' -> count
};

const MAX_ROWS_PER_CELL = 3;

function parseDate(s: string): Date {
  // YYYY-MM-DD → UTC midnight (timezone-safe for date-only math)
  const [y, m, d] = s.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function addDays(d: Date, n: number): Date {
  const r = new Date(d);
  r.setUTCDate(r.getUTCDate() + n);
  return r;
}

function daysBetween(a: Date, b: Date): number {
  return Math.round((b.getTime() - a.getTime()) / 86400000);
}

/** 주의 시작(일요일)으로 정규화 */
function weekStart(d: Date): Date {
  const r = new Date(d);
  r.setUTCDate(r.getUTCDate() - r.getUTCDay());
  return r;
}

export function layoutBars(
  items: PolicyCalendarItem[],
  monthStartStr: string,
  monthEndStr: string,
): LayoutResult {
  const monthStart = parseDate(monthStartStr);
  const monthEnd = parseDate(monthEndStr);
  const gridStart = weekStart(monthStart);

  // 정렬: applyStart 오름차순 (null → gridStart 로 간주), tie 면 더 긴 정책 (applyEnd - applyStart) 먼저
  const sorted = [...items].sort((a, b) => {
    const aStart = a.applyStart ? parseDate(a.applyStart).getTime() : gridStart.getTime();
    const bStart = b.applyStart ? parseDate(b.applyStart).getTime() : gridStart.getTime();
    if (aStart !== bStart) return aStart - bStart;
    const aEnd = a.applyEnd ? parseDate(a.applyEnd).getTime() : Number.MAX_SAFE_INTEGER;
    const bEnd = b.applyEnd ? parseDate(b.applyEnd).getTime() : Number.MAX_SAFE_INTEGER;
    return bEnd - aEnd; // 더 긴 것 먼저
  });

  // 그리드 마지막 주 끝 (토)
  const lastWeekStart = weekStart(monthEnd);
  const gridEnd = addDays(lastWeekStart, 6);
  const totalWeeks = Math.round(daysBetween(gridStart, gridEnd) / 7) + 1;

  // rowOccupancy[weekIndex][row] = 마지막으로 점유된 col (없으면 -1)
  const rowOccupancy: number[][] = Array.from({ length: totalWeeks }, () => []);

  const segments: BarSegment[] = [];
  const overflowByDay: Record<string, number> = {};

  for (const item of sorted) {
    // 실제 표시 시작/끝 (null 처리 + 그리드 클램프)
    const effStart = item.applyStart ? parseDate(item.applyStart) : gridStart;
    const effEnd = item.applyEnd ? parseDate(item.applyEnd) : gridEnd;
    const visibleStart = effStart < gridStart ? gridStart : effStart;
    const visibleEnd = effEnd > gridEnd ? gridEnd : effEnd;
    if (visibleEnd < visibleStart) continue;

    // 항목의 row 결정 — 모든 주에서 통일된 row 를 쓰면 그리드가 일관됨
    // 가장 단순한 휴리스틱: 첫 주 기준으로 row 를 잡고 그 row 가 모든 주에서 가능한지 확인.
    // 그렇지 않으면 다음 row 후보로.
    const firstWeek = Math.floor(daysBetween(gridStart, weekStart(visibleStart)) / 7);
    const lastWeek = Math.floor(daysBetween(gridStart, weekStart(visibleEnd)) / 7);

    let chosenRow = -1;
    for (let row = 0; row < 20; row++) {
      let fits = true;
      for (let w = firstWeek; w <= lastWeek; w++) {
        const last = rowOccupancy[w][row] ?? -1;
        const segStartCol = w === firstWeek ? visibleStart.getUTCDay() : 0;
        if (last >= segStartCol) {
          fits = false;
          break;
        }
      }
      if (fits) {
        chosenRow = row;
        break;
      }
    }

    const isOverflow = chosenRow >= MAX_ROWS_PER_CELL;

    if (isOverflow) {
      // overflow 카운트: 정책이 점유하는 날짜마다 +1
      let cursor = new Date(visibleStart);
      while (cursor <= visibleEnd) {
        const key = formatDate(cursor);
        overflowByDay[key] = (overflowByDay[key] ?? 0) + 1;
        cursor = addDays(cursor, 1);
      }
      continue;
    }

    // 세그먼트 생성 (주 경계로 분할)
    for (let w = firstWeek; w <= lastWeek; w++) {
      const wStartDay = addDays(gridStart, w * 7);
      const wEndDay = addDays(wStartDay, 6);
      const segStart = w === firstWeek ? visibleStart : wStartDay;
      const segEnd = w === lastWeek ? visibleEnd : wEndDay;

      const startCol = segStart.getUTCDay();
      const endCol = segEnd.getUTCDay();

      const hasStartCap =
        item.applyStart !== null &&
        formatDate(segStart) === item.applyStart;
      const hasEndCap =
        item.applyEnd !== null &&
        formatDate(segEnd) === item.applyEnd;

      segments.push({
        itemId: item.id,
        title: item.title,
        category: String(item.category),
        weekIndex: w,
        startCol,
        endCol,
        hasStartCap,
        hasEndCap,
        row: chosenRow,
        isOverflow: false,
        applyStart: item.applyStart,
        applyEnd: item.applyEnd,
      });

      rowOccupancy[w][chosenRow] = endCol;
    }
  }

  return { segments, overflowByDay };
}
```

- [ ] **Step 4: Run tests to confirm they pass**

Run: `cd frontend && npm run test -- src/lib/__tests__/calendarLayout.test.ts`
Expected: all tests pass. 일부 케이스가 알고리즘 휴리스틱과 미세하게 어긋날 수 있음 — 테스트가 의도한 의미는 유지하면서 알고리즘을 조정.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/calendarLayout.ts frontend/src/lib/__tests__/calendarLayout.test.ts
git commit -m "feat(policy): calendarLayout — 막대 레이아웃 순수 함수 + 단위 테스트"
```

---

### Task F3: API 클라이언트 함수 추가

**Files:**
- Modify: `frontend/src/apis/policy.api.ts`

- [ ] **Step 1: Add fetch functions**

기존 `policy.api.ts` 의 import 영역에 추가:

```typescript
import type {
  PolicyPage,
  PolicyDetail,
  PolicyStatus,
  PolicyCalendarResponse,
  PolicyCalendarPageResponse,
} from '@/types/policy';
```

(기존 `import type { PolicyPage, PolicyDetail, PolicyStatus } ...` 라인을 위 내용으로 교체)

파일 끝에 두 함수 추가:

```typescript
interface PolicyCalendarParams {
  from: string;          // YYYY-MM-DD
  to: string;            // YYYY-MM-DD
  regions?: string[];
  category?: string;
}

export async function fetchCalendarPolicies(
  params: PolicyCalendarParams,
): Promise<PolicyCalendarResponse> {
  const searchParams = new URLSearchParams();
  searchParams.set('from', params.from);
  searchParams.set('to', params.to);
  if (params.regions && params.regions.length > 0) {
    searchParams.set('regions', params.regions.join(','));
  }
  if (params.category) searchParams.set('category', params.category);

  return api.get('v1/policies/calendar', { searchParams }).json<PolicyCalendarResponse>();
}

interface AlwaysOpenParams {
  regions?: string[];
  category?: string;
  page?: number;
  size?: number;
}

export async function fetchAlwaysOpenPolicies(
  params: AlwaysOpenParams = {},
): Promise<PolicyCalendarPageResponse> {
  const searchParams = new URLSearchParams();
  if (params.regions && params.regions.length > 0) {
    searchParams.set('regions', params.regions.join(','));
  }
  if (params.category) searchParams.set('category', params.category);
  searchParams.set('page', String(params.page ?? 0));
  searchParams.set('size', String(params.size ?? 20));

  return api.get('v1/policies/calendar/always-open', { searchParams }).json<PolicyCalendarPageResponse>();
}
```

- [ ] **Step 2: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/apis/policy.api.ts
git commit -m "feat(policy): fetchCalendarPolicies·fetchAlwaysOpenPolicies API 추가"
```

---

### Task F4: TanStack Query 훅

**Files:**
- Create: `frontend/src/hooks/queries/usePolicyCalendar.ts`
- Create: `frontend/src/hooks/queries/useAlwaysOpenPolicies.ts`

- [ ] **Step 1: Create `usePolicyCalendar.ts`**

```typescript
import { useQuery } from '@tanstack/react-query';
import { fetchCalendarPolicies } from '@/apis/policy.api';

interface Params {
  from: string;
  to: string;
  regions?: string[];
  category?: string;
}

export function usePolicyCalendar(params: Params) {
  return useQuery({
    queryKey: ['policy', 'calendar', params],
    queryFn: () => fetchCalendarPolicies(params),
  });
}
```

- [ ] **Step 2: Create `useAlwaysOpenPolicies.ts`**

```typescript
import { useQuery } from '@tanstack/react-query';
import { fetchAlwaysOpenPolicies } from '@/apis/policy.api';

interface Params {
  regions?: string[];
  category?: string;
  page?: number;
  size?: number;
}

export function useAlwaysOpenPolicies(params: Params = {}) {
  return useQuery({
    queryKey: ['policy', 'calendar', 'always-open', params],
    queryFn: () => fetchAlwaysOpenPolicies(params),
  });
}
```

- [ ] **Step 3: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/hooks/queries/usePolicyCalendar.ts frontend/src/hooks/queries/useAlwaysOpenPolicies.ts
git commit -m "feat(policy): usePolicyCalendar·useAlwaysOpenPolicies 쿼리 훅"
```

---

### Task F5: PolicyFilterBar 추출

`PolicyListPage.tsx` 의 데스크톱 카테고리 칩 + 모바일 `MobileFilterSheet` + RegionPicker 트리거 부분을 `PolicyFilterBar` 로 추출한다. 두 페이지에서 동일 UX로 재사용.

**Files:**
- Create: `frontend/src/components/policy/PolicyFilterBar.tsx`
- Modify: `frontend/src/pages/PolicyListPage.tsx`

- [ ] **Step 1: Create `PolicyFilterBar.tsx`**

```typescript
import { useEffect, useState } from 'react';
import { X, SlidersHorizontal } from 'lucide-react';
import RegionPicker from '@/components/policy/RegionPicker';
import RegionPickerTrigger from '@/components/policy/RegionPickerTrigger';
import { cn } from '@/lib/cn';
import { CATEGORY_ENTRIES } from '@/lib/constants';
import type { PolicyCategory } from '@/types/policy';
import type { RegionData } from '@/types/region';   // 실제 타입 경로에 맞게 조정

type Props = {
  category: PolicyCategory | '';
  regions: string[];
  regionData: RegionData;
  onCategoryChange: (next: PolicyCategory | '') => void;
  onRegionsChange: (codes: string[]) => void;
  disabled?: boolean;
  disabledHint?: string;
};

export default function PolicyFilterBar({
  category,
  regions,
  regionData,
  onCategoryChange,
  onRegionsChange,
  disabled,
  disabledHint,
}: Props) {
  const [sheetOpen, setSheetOpen] = useState(false);
  const [regionPickerOpen, setRegionPickerOpen] = useState(false);

  const activeCount = (category ? 1 : 0) + (regions.length > 0 ? 1 : 0);

  return (
    <>
      {/* Desktop */}
      <div className="mb-4 hidden flex-wrap items-center gap-2 md:flex">
        {CATEGORY_ENTRIES.map(([key, label]) => (
          <button
            key={key}
            onClick={() => onCategoryChange(category === key ? '' : key)}
            className={cn(
              'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
              category === key
                ? 'border-transparent bg-brand-100 text-indigo-600'
                : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
            )}
          >
            {label}
          </button>
        ))}
        <span className="mx-1 h-6 w-px bg-neutral-200" aria-hidden="true" />
        <div className="relative">
          <RegionPickerTrigger
            selectedCodes={regions}
            regionData={regionData}
            onOpen={() => setRegionPickerOpen(true)}
            disabled={disabled}
          />
          <div className="hidden md:block">
            <RegionPicker
              open={regionPickerOpen}
              onClose={() => setRegionPickerOpen(false)}
              selectedCodes={regions}
              onApply={(codes) => {
                onRegionsChange(codes);
                setRegionPickerOpen(false);
              }}
              regionData={regionData}
              mode="desktop-popover"
            />
          </div>
        </div>
        {disabled && disabledHint && (
          <span className="ml-2 text-xs text-gray-500">{disabledHint}</span>
        )}
      </div>

      {/* Mobile bar */}
      <div className="mb-4 flex flex-wrap items-center gap-2 md:hidden">
        <button
          onClick={() => setSheetOpen(true)}
          className="flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white px-4 py-2 text-sm font-semibold text-neutral-700 transition-colors hover:bg-gray-50"
          aria-label="필터 열기"
        >
          <SlidersHorizontal className="h-4 w-4" />
          필터
          {activeCount > 0 && (
            <span className="ml-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand-800 text-xs text-white">
              {activeCount}
            </span>
          )}
        </button>
        <RegionPickerTrigger
          selectedCodes={regions}
          regionData={regionData}
          onOpen={() => setRegionPickerOpen(true)}
          disabled={disabled}
        />
        {disabled && disabledHint && (
          <span className="text-[10px] text-gray-500">{disabledHint}</span>
        )}
      </div>

      {/* Mobile sheet */}
      <FilterSheet
        isOpen={sheetOpen}
        onClose={() => setSheetOpen(false)}
        category={category}
        onCategoryChange={onCategoryChange}
      />

      {/* Mobile RegionPicker sheet */}
      <div className="md:hidden">
        <RegionPicker
          open={regionPickerOpen}
          onClose={() => setRegionPickerOpen(false)}
          selectedCodes={regions}
          onApply={(codes) => {
            onRegionsChange(codes);
            setRegionPickerOpen(false);
          }}
          regionData={regionData}
          mode="mobile-sheet"
        />
      </div>
    </>
  );
}

function FilterSheet({
  isOpen,
  onClose,
  category,
  onCategoryChange,
}: {
  isOpen: boolean;
  onClose: () => void;
  category: PolicyCategory | '';
  onCategoryChange: (v: PolicyCategory | '') => void;
}) {
  useEffect(() => {
    document.body.style.overflow = isOpen ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} aria-hidden="true" />
      <div className="absolute bottom-0 left-0 right-0 rounded-t-2xl bg-white p-6 pb-8 shadow-xl">
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-900">필터</h2>
          <button
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-gray-100"
            aria-label="필터 닫기"
          >
            <X className="h-5 w-5 text-gray-500" />
          </button>
        </div>
        <fieldset>
          <legend className="mb-2 text-sm font-semibold text-gray-700">카테고리</legend>
          <div className="flex flex-wrap gap-2">
            {CATEGORY_ENTRIES.map(([key, label]) => (
              <button
                key={key}
                onClick={() => onCategoryChange(category === key ? '' : key)}
                className={cn(
                  'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
                  category === key
                    ? 'border-transparent bg-brand-100 text-indigo-600'
                    : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
                )}
              >
                {label}
              </button>
            ))}
          </div>
        </fieldset>
        <button
          onClick={onClose}
          className="mt-6 w-full rounded-xl bg-brand-800 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
        >
          필터 적용
        </button>
      </div>
    </div>
  );
}
```

> 주의: 위 코드의 `RegionData` 타입 import 경로, `CATEGORY_ENTRIES` 위치는 기존 `PolicyListPage.tsx` 의 import 와 일치시킨다. `regionData`/`onApply` 시그니처는 `RegionPicker` 의 실제 props 에 맞추는 것이 중요 — 추출 작업이므로 동작은 그대로여야 한다.

- [ ] **Step 2: Update `PolicyListPage.tsx` to use `PolicyFilterBar`**

`PolicyListPage.tsx` 에서:
1. 인라인 `MobileFilterSheet` 함수 정의 (62~140줄 근처) 삭제
2. import 에 `PolicyFilterBar` 추가, 사용하지 않게 된 `X`, `SlidersHorizontal`, `RegionPickerTrigger`, `RegionPicker` import 제거
3. 데스크톱 필터 (484~524줄) + 모바일 필터 바 (527~550줄) + `MobileFilterSheet` (552~557줄) + 모바일 RegionPicker (560줄 부근) 블록을 다음으로 교체:

```tsx
<PolicyFilterBar
  category={category}
  regions={regions}
  regionData={regionData}
  onCategoryChange={(v) => updateParams({ category: v, page: '' })}
  onRegionsChange={handleRegionApply}
  disabled={isSearchMode}
  disabledHint="검색 결과에는 지역 필터가 적용되지 않습니다"
/>
```

> `handleRegionApply` 와 `regionData`, `isSearchMode` 는 기존 변수 그대로. 정확한 라인 번호는 변경 전 파일 기준이라 상황에 맞춰 식별한다.

- [ ] **Step 3: Build + lint check**

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: build success

- [ ] **Step 4: Run existing tests**

Run: `cd frontend && npm run test`
Expected: 기존 테스트 모두 통과 (PolicyListPage 관련 테스트가 있다면 검토)

- [ ] **Step 5: Manual smoke test**

```bash
cd frontend && npm run dev
```
브라우저에서 `/policies` 진입 → 데스크톱/모바일 폭에서 카테고리 칩, 지역 필터, 모바일 시트가 *추출 전과 똑같이* 동작하는지 확인.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/policy/PolicyFilterBar.tsx \
        frontend/src/pages/PolicyListPage.tsx
git commit -m "refactor(policy): PolicyFilterBar 추출 — 리스트·달력 페이지 공유"
```

---

## Phase 3 — Frontend UI

### Task F6: CalendarBar 컴포넌트

한 막대(한 주에서의 한 세그먼트)를 그리는 단순 표현 컴포넌트.

**Files:**
- Create: `frontend/src/components/policy-calendar/CalendarBar.tsx`

- [ ] **Step 1: Implement `CalendarBar`**

```typescript
import { Link } from 'react-router-dom';
import { cn } from '@/lib/cn';
import type { BarSegment } from '@/lib/calendarLayout';

const CATEGORY_TONE: Record<string, string> = {
  HOUSING: 'bg-blue-100 text-blue-800 border-blue-300',
  JOB: 'bg-amber-100 text-amber-800 border-amber-300',
  EDUCATION: 'bg-green-100 text-green-800 border-green-300',
  WELFARE: 'bg-violet-100 text-violet-800 border-violet-300',
  OTHER: 'bg-gray-100 text-gray-700 border-gray-300',
};

type Props = {
  segment: BarSegment;
  showLabel: boolean;           // 이 세그먼트에 라벨 텍스트를 보여줄지
  daysUntilEnd: number | null;  // null 이면 마감일 없음
};

export default function CalendarBar({ segment, showLabel, daysUntilEnd }: Props) {
  const tone = CATEGORY_TONE[segment.category] ?? CATEGORY_TONE.OTHER;
  const urgent = daysUntilEnd !== null && daysUntilEnd <= 3 && daysUntilEnd >= 0;

  const widthCols = segment.endCol - segment.startCol + 1;

  return (
    <Link
      to={`/policies/${segment.itemId}`}
      className={cn(
        'flex items-center gap-1 border px-2 py-0.5 text-xs font-semibold truncate',
        tone,
        segment.hasStartCap ? 'rounded-l-md' : 'rounded-l-none border-l-0',
        segment.hasEndCap ? 'rounded-r-md' : 'rounded-r-none border-r-0',
        urgent && 'ring-2 ring-red-500',
      )}
      style={{
        gridColumn: `${segment.startCol + 1} / span ${widthCols}`,
      }}
      title={`${segment.title} · ${segment.applyStart ?? '?'}~${segment.applyEnd ?? '?'}${
        daysUntilEnd !== null ? ` · 마감 D-${daysUntilEnd}` : ''
      }`}
    >
      {showLabel && (
        <>
          <span className="truncate">{segment.title}</span>
          {urgent && <span className="ml-auto shrink-0">D-{daysUntilEnd}</span>}
        </>
      )}
    </Link>
  );
}
```

- [ ] **Step 2: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/policy-calendar/CalendarBar.tsx
git commit -m "feat(policy): CalendarBar — 정책 막대 표현 컴포넌트"
```

---

### Task F7: CalendarMonthGrid 컴포넌트

월 그리드(데스크톱). 셀 헤더 + 막대 행 + +N 오버플로우.

**Files:**
- Create: `frontend/src/components/policy-calendar/CalendarMonthGrid.tsx`

- [ ] **Step 1: Implement**

```typescript
import { useMemo, useState } from 'react';
import { cn } from '@/lib/cn';
import { layoutBars, type BarSegment } from '@/lib/calendarLayout';
import CalendarBar from './CalendarBar';
import CalendarDayPopover from './CalendarDayPopover';
import type { PolicyCalendarItem } from '@/types/policy';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

type Props = {
  items: PolicyCalendarItem[];
  monthStart: string;   // YYYY-MM-01
  monthEnd: string;     // YYYY-MM-LASTDAY
  today: string;        // YYYY-MM-DD (KST)
};

function parseDate(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}
function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}
function addDays(d: Date, n: number): Date {
  const r = new Date(d);
  r.setUTCDate(r.getUTCDate() + n);
  return r;
}
function weekStart(d: Date): Date {
  const r = new Date(d);
  r.setUTCDate(r.getUTCDate() - r.getUTCDay());
  return r;
}

export default function CalendarMonthGrid({ items, monthStart, monthEnd, today }: Props) {
  const [popoverDay, setPopoverDay] = useState<string | null>(null);

  const { layout, weeks } = useMemo(() => {
    const layout = layoutBars(items, monthStart, monthEnd);

    const start = weekStart(parseDate(monthStart));
    const end = parseDate(monthEnd);
    const lastWeek = weekStart(end);
    const lastDay = addDays(lastWeek, 6);
    const totalDays = Math.round((lastDay.getTime() - start.getTime()) / 86400000) + 1;
    const totalWeeks = totalDays / 7;

    const weeks: Array<Array<string>> = [];
    for (let w = 0; w < totalWeeks; w++) {
      const week: string[] = [];
      for (let d = 0; d < 7; d++) {
        week.push(formatDate(addDays(start, w * 7 + d)));
      }
      weeks.push(week);
    }
    return { layout, weeks };
  }, [items, monthStart, monthEnd]);

  const segmentsByWeek = useMemo(() => {
    const m = new Map<number, BarSegment[]>();
    for (const seg of layout.segments) {
      const arr = m.get(seg.weekIndex) ?? [];
      arr.push(seg);
      m.set(seg.weekIndex, arr);
    }
    return m;
  }, [layout.segments]);

  const monthNum = Number(monthStart.slice(5, 7));

  const todayDate = parseDate(today);
  const daysUntil = (end: string | null) =>
    end ? Math.round((parseDate(end).getTime() - todayDate.getTime()) / 86400000) : null;

  // 라벨 노출 — 정책 id 당 최초 노출 세그먼트만 true
  const labelShown = new Set<number>();

  return (
    <div className="w-full">
      {/* 요일 헤더 */}
      <div className="grid grid-cols-7 border-b border-neutral-200 text-xs font-semibold text-neutral-600">
        {WEEKDAYS.map((d, i) => (
          <div
            key={d}
            className={cn(
              'py-2 text-center',
              i === 0 && 'text-red-500',
              i === 6 && 'text-blue-500',
            )}
          >
            {d}
          </div>
        ))}
      </div>

      {/* 주별 행 */}
      <div className="flex flex-col">
        {weeks.map((week, wi) => {
          const segs = segmentsByWeek.get(wi) ?? [];
          const maxRow = Math.max(0, ...segs.map((s) => s.row));
          const rowsToRender = Math.max(2, maxRow + 1); // 최소 2행 시각적 여백

          return (
            <div key={wi} className="border-b border-neutral-100">
              {/* 날짜 헤더 */}
              <div className="grid grid-cols-7">
                {week.map((day) => {
                  const inMonth = Number(day.slice(5, 7)) === monthNum;
                  const isToday = day === today;
                  return (
                    <div
                      key={day}
                      className={cn(
                        'px-2 pt-1 text-xs',
                        inMonth ? 'text-neutral-800' : 'text-neutral-400',
                        isToday && 'font-bold text-brand-800',
                      )}
                    >
                      {Number(day.slice(8, 10))}
                    </div>
                  );
                })}
              </div>

              {/* 막대 행 (최대 3행) */}
              <div
                className="grid grid-cols-7 gap-y-0.5 px-1 pb-1"
                style={{ gridAutoRows: '20px' }}
              >
                {segs
                  .filter((s) => s.row < 3)
                  .map((seg) => {
                    const show = !labelShown.has(seg.itemId);
                    if (show) labelShown.add(seg.itemId);
                    return (
                      <div
                        key={`${seg.itemId}-${seg.weekIndex}`}
                        style={{ gridRow: seg.row + 1 }}
                      >
                        <CalendarBar
                          segment={seg}
                          showLabel={show && seg.hasStartCap}
                          daysUntilEnd={daysUntil(seg.applyEnd)}
                        />
                      </div>
                    );
                  })}
              </div>

              {/* +N more 행 */}
              <div className="grid grid-cols-7 px-1 pb-1">
                {week.map((day) => {
                  const overflow = layout.overflowByDay[day] ?? 0;
                  if (overflow <= 0) return <div key={day} />;
                  return (
                    <button
                      key={day}
                      onClick={() => setPopoverDay(day)}
                      className="text-[10px] font-semibold text-neutral-500 hover:text-brand-800"
                    >
                      +{overflow}개 더보기
                    </button>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      {popoverDay && (
        <CalendarDayPopover
          day={popoverDay}
          items={items.filter((it) => {
            const start = it.applyStart ? parseDate(it.applyStart) : null;
            const end = it.applyEnd ? parseDate(it.applyEnd) : null;
            const target = parseDate(popoverDay);
            const afterStart = !start || target >= start;
            const beforeEnd = !end || target <= end;
            return afterStart && beforeEnd;
          })}
          onClose={() => setPopoverDay(null)}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 2: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: `CalendarDayPopover` import 가 미존재 (다음 태스크에서 만듦). 일시적으로 빈 stub 으로 두거나 다음 태스크와 묶어서 진행.

> 권장: 이 태스크와 F9 를 묶어서 한 번에 진행할 경우 stub 없이 직접 작성. 분리해서 진행할 경우 임시 stub 컴포넌트:
> ```tsx
> // 임시 stub — Task F9 에서 교체됨
> export default function CalendarDayPopover() { return null; }
> ```

- [ ] **Step 3: Commit (stub 사용 시)**

```bash
git add frontend/src/components/policy-calendar/CalendarMonthGrid.tsx
git commit -m "feat(policy): CalendarMonthGrid — 월 그리드 + 막대 행"
```

---

### Task F8: CalendarAgenda 컴포넌트 (모바일)

**Files:**
- Create: `frontend/src/components/policy-calendar/CalendarAgenda.tsx`

- [ ] **Step 1: Implement**

```typescript
import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import type { PolicyCalendarItem } from '@/types/policy';

type Event = {
  day: string;       // YYYY-MM-DD
  weekday: string;
  kind: 'start' | 'end';
  item: PolicyCalendarItem;
  daysUntil: number;
};

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function parseDate(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

export default function CalendarAgenda({
  items,
  monthStart,
  monthEnd,
  today,
}: {
  items: PolicyCalendarItem[];
  monthStart: string;
  monthEnd: string;
  today: string;
}) {
  const events = useMemo<Event[]>(() => {
    const start = parseDate(monthStart);
    const end = parseDate(monthEnd);
    const todayD = parseDate(today);
    const result: Event[] = [];

    for (const item of items) {
      if (item.applyStart) {
        const d = parseDate(item.applyStart);
        if (d >= start && d <= end) {
          result.push({
            day: item.applyStart,
            weekday: WEEKDAYS[d.getUTCDay()],
            kind: 'start',
            item,
            daysUntil: Math.round((d.getTime() - todayD.getTime()) / 86400000),
          });
        }
      }
      if (item.applyEnd) {
        const d = parseDate(item.applyEnd);
        if (d >= start && d <= end) {
          result.push({
            day: item.applyEnd,
            weekday: WEEKDAYS[d.getUTCDay()],
            kind: 'end',
            item,
            daysUntil: Math.round((d.getTime() - todayD.getTime()) / 86400000),
          });
        }
      }
    }

    return result.sort((a, b) => (a.day < b.day ? -1 : a.day > b.day ? 1 : 0));
  }, [items, monthStart, monthEnd, today]);

  // group by day
  const groups = useMemo(() => {
    const map = new Map<string, Event[]>();
    for (const e of events) {
      const arr = map.get(e.day) ?? [];
      arr.push(e);
      map.set(e.day, arr);
    }
    return Array.from(map.entries());
  }, [events]);

  if (groups.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-neutral-500">
        이 달에는 신청 기간이 걸친 정책이 없어요.
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-4">
      {groups.map(([day, evs]) => (
        <li key={day}>
          <h3 className="mb-1 text-sm font-semibold text-neutral-700">
            {Number(day.slice(5, 7))}/{Number(day.slice(8, 10))} ({evs[0].weekday})
          </h3>
          <ul className="flex flex-col gap-1.5">
            {evs.map((e) => {
              const urgent = e.kind === 'end' && e.daysUntil >= 0 && e.daysUntil <= 3;
              return (
                <li key={`${e.item.id}-${e.kind}`}>
                  <Link
                    to={`/policies/${e.item.id}`}
                    className="flex items-center gap-2 rounded-lg border border-neutral-200 bg-white px-3 py-2 text-sm hover:bg-neutral-50"
                  >
                    <span
                      className={
                        e.kind === 'end' ? 'text-red-500' : 'text-brand-800'
                      }
                    >
                      ┃
                    </span>
                    <span className="flex-1 font-medium text-neutral-900 truncate">
                      {e.item.title}
                    </span>
                    <span className="text-xs text-neutral-500">
                      {e.kind === 'start' ? '시작' : '마감'}
                      {urgent && ` · D-${e.daysUntil}❗`}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </li>
      ))}
    </ul>
  );
}
```

- [ ] **Step 2: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/policy-calendar/CalendarAgenda.tsx
git commit -m "feat(policy): CalendarAgenda — 모바일 아젠다 리스트 뷰"
```

---

### Task F9: CalendarDayPopover, AlwaysOpenSection, CalendarHeader

세 작은 컴포넌트를 한 묶음으로.

**Files:**
- Create: `frontend/src/components/policy-calendar/CalendarDayPopover.tsx`
- Create: `frontend/src/components/policy-calendar/AlwaysOpenSection.tsx`
- Create: `frontend/src/components/policy-calendar/CalendarHeader.tsx`

- [ ] **Step 1: `CalendarDayPopover`**

```typescript
import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import type { PolicyCalendarItem } from '@/types/policy';

type Props = {
  day: string; // YYYY-MM-DD
  items: PolicyCalendarItem[];
  onClose: () => void;
};

export default function CalendarDayPopover({ day, items, onClose }: Props) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
      role="dialog"
      aria-label={`${day} 정책 목록`}
    >
      <div
        className="w-full max-w-md max-h-[80vh] overflow-y-auto rounded-2xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-neutral-100 px-5 py-3">
          <h2 className="text-sm font-bold text-neutral-900">
            {Number(day.slice(5, 7))}월 {Number(day.slice(8, 10))}일 정책 ({items.length}건)
          </h2>
          <button
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full hover:bg-neutral-100"
            aria-label="닫기"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <ul className="flex flex-col">
          {items.map((it) => (
            <li key={it.id} className="border-b border-neutral-100 last:border-b-0">
              <Link
                to={`/policies/${it.id}`}
                className="block px-5 py-3 text-sm hover:bg-neutral-50"
              >
                <div className="font-medium text-neutral-900">{it.title}</div>
                <div className="mt-0.5 text-xs text-neutral-500">
                  {it.applyStart ?? '?'} ~ {it.applyEnd ?? '?'} · {it.regionLabel}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: `AlwaysOpenSection`**

```typescript
import { Link } from 'react-router-dom';
import { useAlwaysOpenPolicies } from '@/hooks/queries/useAlwaysOpenPolicies';

type Props = {
  regions: string[];
  category: string;
};

export default function AlwaysOpenSection({ regions, category }: Props) {
  const { data, isLoading } = useAlwaysOpenPolicies({
    regions,
    category: category || undefined,
    page: 0,
    size: 5,
  });

  if (isLoading) {
    return (
      <section className="mt-6">
        <div className="h-20 animate-pulse rounded-xl bg-neutral-100" />
      </section>
    );
  }

  if (!data || data.totalCount === 0) return null;

  const params = new URLSearchParams();
  if (regions.length > 0) params.set('regions', regions.join(','));
  if (category) params.set('category', category);
  params.set('alwaysOpen', '1'); // PolicyListPage 가 인식할 깃발 — v0 에서는 단순 링크용
  const moreHref = `/policies?${params.toString()}`;

  return (
    <section className="mt-6 rounded-2xl border border-neutral-200 bg-white p-4">
      <header className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-bold text-neutral-900">
          상시 모집 정책 · {data.totalCount}건
        </h2>
        <Link to={moreHref} className="text-xs font-semibold text-brand-800 hover:underline">
          모두 보기 →
        </Link>
      </header>
      <div className="flex flex-wrap gap-2">
        {data.content.map((it) => (
          <Link
            key={it.id}
            to={`/policies/${it.id}`}
            className="rounded-full border border-neutral-200 bg-neutral-50 px-3 py-1.5 text-xs font-medium text-neutral-700 hover:bg-neutral-100"
          >
            {it.title}
          </Link>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 3: `CalendarHeader`**

```typescript
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/cn';

type Props = {
  month: string;        // YYYY-MM
  todayMonth: string;   // YYYY-MM
  onPrev: () => void;
  onNext: () => void;
  onToday: () => void;
};

export default function CalendarHeader({ month, todayMonth, onPrev, onNext, onToday }: Props) {
  const [y, m] = month.split('-').map(Number);
  const isToday = month === todayMonth;

  return (
    <header className="mb-4 flex items-center justify-between">
      <div className="flex items-center gap-2">
        <button
          onClick={onPrev}
          aria-label="이전 달"
          className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-neutral-100"
        >
          <ChevronLeft className="h-5 w-5" />
        </button>
        <h1 className="text-lg font-bold text-neutral-900">
          {y}년 {m}월
        </h1>
        <button
          onClick={onNext}
          aria-label="다음 달"
          className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-neutral-100"
        >
          <ChevronRight className="h-5 w-5" />
        </button>
      </div>
      <button
        onClick={onToday}
        disabled={isToday}
        className={cn(
          'rounded-full border border-neutral-200 bg-white px-4 py-2 text-sm font-semibold',
          isToday ? 'cursor-not-allowed text-neutral-400' : 'text-neutral-700 hover:bg-neutral-50',
        )}
      >
        오늘로
      </button>
    </header>
  );
}
```

- [ ] **Step 4: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/policy-calendar/CalendarDayPopover.tsx \
        frontend/src/components/policy-calendar/AlwaysOpenSection.tsx \
        frontend/src/components/policy-calendar/CalendarHeader.tsx
git commit -m "feat(policy): CalendarDayPopover·AlwaysOpenSection·CalendarHeader"
```

---

### Task F10: PolicyCalendarPage + 라우트 + Navbar

**Files:**
- Create: `frontend/src/pages/PolicyCalendarPage.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/layout/Navbar.tsx`

- [ ] **Step 1: Implement `PolicyCalendarPage`**

```typescript
import { useMemo, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMediaQuery } from '@/hooks/useMediaQuery';   // 기존 훅. 없으면 만들거나 inline matchMedia
import { usePolicyCalendar } from '@/hooks/queries/usePolicyCalendar';
import { useRegionData } from '@/hooks/queries/useRegionData'; // PolicyListPage 와 동일
import PolicyFilterBar from '@/components/policy/PolicyFilterBar';
import CalendarHeader from '@/components/policy-calendar/CalendarHeader';
import CalendarMonthGrid from '@/components/policy-calendar/CalendarMonthGrid';
import CalendarAgenda from '@/components/policy-calendar/CalendarAgenda';
import AlwaysOpenSection from '@/components/policy-calendar/AlwaysOpenSection';
import type { PolicyCategory } from '@/types/policy';

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function todayKST(): string {
  return new Date().toISOString().slice(0, 10);
}

function monthBoundaries(month: string): { start: string; end: string } {
  const [y, m] = month.split('-').map(Number);
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return {
    start: `${month}-01`,
    end: `${month}-${String(last).padStart(2, '0')}`,
  };
}

function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1 + delta, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}

export default function PolicyCalendarPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const month = searchParams.get('month') ?? currentMonth();
  const regions = (searchParams.get('regions') ?? '').split(',').filter(Boolean);
  const category = (searchParams.get('category') ?? '') as PolicyCategory | '';

  const isDesktop = useMediaQuery('(min-width: 768px)');
  const { start: monthStart, end: monthEnd } = useMemo(() => monthBoundaries(month), [month]);
  const today = useMemo(() => todayKST(), []);
  const todayMonth = today.slice(0, 7);

  const { data: regionData } = useRegionData();
  const { data, isLoading, error } = usePolicyCalendar({
    from: monthStart,
    to: monthEnd,
    regions: regions.length > 0 ? regions : undefined,
    category: category || undefined,
  });

  const updateParams = (patch: Record<string, string>) => {
    const next = new URLSearchParams(searchParams);
    for (const [k, v] of Object.entries(patch)) {
      if (v === '') next.delete(k);
      else next.set(k, v);
    }
    setSearchParams(next);
  };

  // 키보드 ←→ 단축키
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
      if (e.key === 'ArrowLeft') updateParams({ month: shiftMonth(month, -1) });
      else if (e.key === 'ArrowRight') updateParams({ month: shiftMonth(month, +1) });
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [month]);

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 md:px-6 md:py-8">
      <CalendarHeader
        month={month}
        todayMonth={todayMonth}
        onPrev={() => updateParams({ month: shiftMonth(month, -1) })}
        onNext={() => updateParams({ month: shiftMonth(month, +1) })}
        onToday={() => updateParams({ month: todayMonth })}
      />

      {regionData && (
        <PolicyFilterBar
          category={category}
          regions={regions}
          regionData={regionData}
          onCategoryChange={(v) => updateParams({ category: v })}
          onRegionsChange={(codes) =>
            updateParams({ regions: codes.length > 0 ? codes.join(',') : '' })
          }
        />
      )}

      {error && (
        <div className="my-8 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          정책을 불러오지 못했습니다. 다시 시도해 주세요.
        </div>
      )}

      {isLoading ? (
        <div className="h-96 animate-pulse rounded-xl bg-neutral-100" />
      ) : (
        <>
          {isDesktop ? (
            <CalendarMonthGrid
              items={data?.items ?? []}
              monthStart={monthStart}
              monthEnd={monthEnd}
              today={today}
            />
          ) : (
            <CalendarAgenda
              items={data?.items ?? []}
              monthStart={monthStart}
              monthEnd={monthEnd}
              today={today}
            />
          )}
        </>
      )}

      <AlwaysOpenSection regions={regions} category={category} />
    </div>
  );
}
```

> 확인: `useMediaQuery` 가 프로젝트에 이미 있는지 (`grep -r "useMediaQuery" frontend/src`). 없다면 다음 stub 으로 만든다:
> ```typescript
> // frontend/src/hooks/useMediaQuery.ts
> import { useEffect, useState } from 'react';
> export function useMediaQuery(query: string): boolean {
>   const [matches, setMatches] = useState(() =>
>     typeof window !== 'undefined' ? window.matchMedia(query).matches : false,
>   );
>   useEffect(() => {
>     const mql = window.matchMedia(query);
>     const onChange = (e: MediaQueryListEvent) => setMatches(e.matches);
>     mql.addEventListener('change', onChange);
>     setMatches(mql.matches);
>     return () => mql.removeEventListener('change', onChange);
>   }, [query]);
>   return matches;
> }
> ```
> 마찬가지로 `useRegionData` 가 없으면 `PolicyListPage.tsx` 에서 사용 중인 동일 패턴을 따른다 — `grep regionData frontend/src/pages/PolicyListPage.tsx` 로 확인.

- [ ] **Step 2: Add route to `App.tsx`**

import 추가:

```tsx
import PolicyCalendarPage from '@/pages/PolicyCalendarPage';
```

`<Route element={<AppLayout />}>` 안에서 `/policies` 라우트 *바로 다음 줄* 에 추가:

```tsx
<Route path="/policies/calendar" element={<PolicyCalendarPage />} />
```

> 중요: `/policies/:policyId` 보다 *앞* 에 두어야 `calendar` 가 `policyId` 로 매칭되지 않는다.

- [ ] **Step 3: Add Navbar item**

`frontend/src/components/layout/Navbar.tsx` 의 `NAV_LINKS` 배열을 다음으로 교체:

```tsx
const NAV_LINKS = [
  { to: '/policies', label: '정책 목록' },
  { to: '/policies/calendar', label: '정책 달력' },
  { to: '/policies#eligibility', label: '적합도 판정' },
  { to: '/policies#qna', label: 'Q&A' },
];
```

> 활성 표시 로직(`location.pathname === link.to`)이 `/policies/calendar` 에서 정책 달력 칩에만 활성화되는지 확인. 현 코드가 정확히 같은 path 비교라서 OK.

- [ ] **Step 4: Type check + build**

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: build success

- [ ] **Step 5: Run all tests**

Run: `cd frontend && npm run test`
Expected: all green

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/PolicyCalendarPage.tsx \
        frontend/src/App.tsx \
        frontend/src/components/layout/Navbar.tsx \
        frontend/src/hooks/useMediaQuery.ts   # 새로 만들었다면
git commit -m "feat(policy): 정책 달력 페이지 + 라우트 + Navbar 항목 추가"
```

---

## Phase 4 — Integration & Verification

### Task I1: 수동 검증

CLAUDE.md 의 "UI 변경은 브라우저로 확인" 규칙 준수.

- [ ] **Step 1: Start dev servers**

```bash
cd backend && ./gradlew bootRun &
cd frontend && npm run dev &
```

- [ ] **Step 2: 데스크톱 (1280px) 검증**

브라우저에서 `http://localhost:5173/policies/calendar`:
- 상단 네비에 "정책 달력" 표시되고 활성 상태
- 월 그리드가 7×N 으로 그려지고, 정책 막대가 시작·마감 캡 모양과 함께 보임
- 카테고리 칩 클릭 → 필터 적용 + URL 갱신
- 지역 필터 → 필터 적용
- 키보드 ←/→ 로 월 이동
- "오늘로" 버튼 동작
- 마감 D-3 이내 정책에 빨강 보더 표시
- 셀에 3개 이상 정책이면 `+N개 더보기` 칩, 클릭 시 popover 열림
- 막대 클릭 → `/policies/:id` 이동
- 그리드 아래 "상시 모집 정책" 섹션 표시

- [ ] **Step 3: 모바일 (375px) 검증**

DevTools 디바이스 모드 또는 폭 좁히기:
- 그리드 대신 아젠다 리스트 표시
- 날짜별로 그룹화된 시작/마감 이벤트
- 마감 이벤트는 빨강 `┃`, 시작은 파랑 `┃`
- D-3 이내 마감 이벤트에 `❗` 노출
- 필터 버튼 → 시트 열림 (기존 PolicyListPage 모바일 시트와 동일 UX)

- [ ] **Step 4: 빈 상태 검증**

URL 에 `?regions=99999` 같은 매칭 없는 필터 적용 → "신청 기간이 걸친 정책이 없어요" 메시지 (CalendarAgenda 의 경우) 또는 빈 그리드.

- [ ] **Step 5: 정책 목록 페이지 회귀 검증**

`http://localhost:5173/policies`:
- 카테고리 칩, 지역 필터, 모바일 시트 모두 *추출 전과 동일* 하게 동작
- 키워드 검색, 상태 탭, 정렬, 페이지네이션 정상

- [ ] **Step 6: 백엔드 에러 케이스 (curl)**

```bash
curl -i "http://localhost:8080/api/v1/policies/calendar?from=2026-03-31&to=2026-03-01"
# Expected: HTTP/1.1 400

curl -i "http://localhost:8080/api/v1/policies/calendar?from=2026-01-01&to=2026-12-31"
# Expected: HTTP/1.1 400 (범위 초과)

curl -s "http://localhost:8080/api/v1/policies/calendar?from=2026-05-01&to=2026-05-31" | jq '.items | length'
# Expected: number ≥ 0
```

- [ ] **Step 7: 정리 & 최종 커밋**

별도 fix 가 필요 없으면 PR 생성으로 진행:

```bash
git status   # clean 확인
```

---

## Self-Review Notes

- **Spec coverage**:
  - §3 진입점 → F10 Navbar + App.tsx
  - §4.1 그리드 → F6/F7
  - §4.2 아젠다 → F8
  - §4.3 상시 정책 → F9 (AlwaysOpenSection)
  - §5 헤더 → F9 (CalendarHeader)
  - §6 필터 통합 → F5 (PolicyFilterBar 추출)
  - §7 백엔드 API → B1~B5
  - §8 프론트 모듈 → F1~F10
  - §9 인터랙션 → F6/F7/F9/F10
  - §10 로딩/에러 → F10
  - §11 접근성 → F6 (Link/button), F9 (aria-label)
  - §12 엣지케이스 → B2/B4 (백엔드), F2 (프론트 layout 함수)
  - §13 테스트 전략 → B2/B4/B5/F2
  - §14 구현 순서 → 일치

- **Placeholders**: 없음

- **타입 일관성**: `PolicyCalendarResult`/`Response`, `PolicyCalendarItem` 사용 일관. `BarSegment` F2에서 정의 → F6/F7 에서 import.
