# Policy Source Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책 목록에 출처(온통청년·복지로·청년서울) 단일 선택 필터를 추가한다. URL `?source=...` 로 상태 보존, 검색 모드에서는 disabled.

**Architecture:** 백엔드는 `PolicySpecification.withFilters` 에 EXISTS 서브쿼리(`policy_source` 테이블) 를 추가하고 시그니처를 `SourceType source` 인자로 확장한다. 프론트는 카테고리 칩과 같은 패턴의 단일 선택 chip group 을 `PolicyFilterBar` 에 추가하고, `PolicyListPage` 가 URL 파라미터로 상태를 관리한다.

**Tech Stack:** Spring Boot 4.0.5 / JPA Criteria API + Specification, JUnit 5 + Mockito, React 19 + TypeScript 5, TanStack Query v5, React Router v7, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-05-28-policy-source-filter-design.md`

---

## File Structure

### Backend — modify only

| 파일 | 책임 |
|---|---|
| `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java` | `withFilters(regionFilter, category, status, source)` — EXISTS 서브쿼리로 sourceType 필터링 |
| `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java` | 포트 시그니처에 `SourceType source` 추가 |
| `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java` | Specification 호출에 source 전달 |
| `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java` | `findPoliciesByFilters` 시그니처에 source 추가, repository 로 전달 |
| `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyApi.java` | `findPolicies` 메서드 시그니처에 `SourceType source` + `@Parameter` |
| `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java` | `@RequestParam(required=false) SourceType source` 수신, service 전달 |

### Backend tests — modify

| 파일 | 책임 |
|---|---|
| `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationTest.java` | `withFilters` 시그니처 변경에 따른 호출부 수정 + source predicate 새 테스트 |
| `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java` | service 시그니처 변경에 따른 호출부 수정 + source 전달 회귀 |
| `backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyControllerTest.java` | Mock argument 추가, `?source=YOUTH_CENTER` 케이스 + invalid enum 400 케이스 |

### Frontend — modify only

| 파일 | 책임 |
|---|---|
| `frontend/src/types/policy.ts` | `SOURCE_LABELS` Record 추가, `isSourceType` 타입 가드 |
| `frontend/src/apis/policy.api.ts` | `fetchPolicies` params 에 `source?: SourceType` |
| `frontend/src/hooks/queries/usePolicies.ts` | `source` 파라미터 — queryKey + fetchPolicies 호출 |
| `frontend/src/components/policy/PolicyFilterBar.tsx` | 출처 chip group — 데스크톱 라인 + 모바일 시트 fieldset |
| `frontend/src/pages/PolicyListPage.tsx` | `?source=` 읽기·쓰기, `usePolicies` 전달, active filter badge |

### Convention Notes

- **백엔드:** `backend/CLAUDE.md` + `.claude/rules/backend/*` 참조. DDD 의존 방향 유지. record DTO, Lombok `@Getter`/`@Builder` 만, 도메인 setter 금지.
- **프론트:** `frontend/CLAUDE.md` + `.claude/rules/frontend/*` 참조. Tailwind 유틸 우선, `cn()` 사용, 터치 타겟 44×44, URL 상태는 React Router searchParams.
- **테스트 명령:**
  - 백엔드 전체: `cd backend && ./gradlew test`
  - 백엔드 단일: `cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicySpecificationTest"`
  - 프론트 전체: `cd frontend && npm run test`
  - 프론트 타입체크 + 빌드: `cd frontend && npm run build`

---

## Task 1: Backend — PolicySpecification 시그니처 확장 + EXISTS predicate

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java`
- Modify: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationTest.java`

### Step 1.1 — 기존 테스트 호출부 수정 (시그니처 변경 빌드 실패 방지)

- [ ] **`PolicySpecificationTest.java` 의 모든 `PolicySpecification.withFilters(...)` 호출에 4번째 인자 `null` 추가**

기존 호출은 `PolicySpecification.withFilters(RegionFilter.of(null), null, null)` 형태. `null` 인자 하나를 추가해서 `PolicySpecification.withFilters(RegionFilter.of(null), null, null, null)` 로 바꾼다. 파일 안의 모든 `withFilters(` 호출에 동일 적용.

### Step 1.2 — `source != null` 일 때 EXISTS predicate 가 추가되는 신규 테스트 작성

- [ ] **`PolicySpecificationTest.java` 에 다음 `@Nested` 클래스 또는 `@Test` 메서드 추가**

기존 mock 패턴(BeforeEach setUpSelectCaseChain) 위에 다음 테스트를 추가:

```java
@Test
@DisplayName("withFilters: source가 주어지면 EXISTS 서브쿼리 predicate를 추가한다")
@SuppressWarnings({"unchecked", "rawtypes"})
void withFilters_withSource_addsExistsSubquery() {
    // given
    Predicate conjunction = mock(Predicate.class);
    Path<Object> createdAtPath = mock(Path.class);
    Order descOrder = mock(Order.class);
    Subquery<Long> subquery = mock(Subquery.class, RETURNS_DEEP_STUBS);
    Root<com.youthfit.policy.domain.model.PolicySource> sourceRoot =
            mock(Root.class, RETURNS_DEEP_STUBS);
    Predicate existsPredicate = mock(Predicate.class);

    given(cb.and(any(Predicate[].class))).willReturn(conjunction);
    given(query.getResultType()).willReturn((Class) Policy.class);
    given(query.subquery(Long.class)).willReturn(subquery);
    given(subquery.from(com.youthfit.policy.domain.model.PolicySource.class)).willReturn(sourceRoot);
    given(subquery.select(any())).willReturn(subquery);
    given(subquery.where(any(Predicate.class), any(Predicate.class))).willReturn(subquery);
    given(cb.exists(subquery)).willReturn(existsPredicate);
    given(root.get("createdAt")).willReturn(createdAtPath);
    given(cb.desc(createdAtPath)).willReturn(descOrder);

    Specification<Policy> spec = PolicySpecification.withFilters(
            RegionFilter.of(null), null, null,
            com.youthfit.policy.domain.model.SourceType.BOKJIRO_CENTRAL);

    // when
    spec.toPredicate(root, query, cb);

    // then — exists 서브쿼리가 한 번 이상 생성되었음
    then(query).should().subquery(Long.class);
    then(cb).should().exists(subquery);
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
```

추가 import:
```java
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.model.PolicySource;
import jakarta.persistence.criteria.Subquery;
```

### Step 1.3 — 테스트 실행해서 실패 확인

- [ ] **명령:**
```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicySpecificationTest"
```

기대: 컴파일 실패 — `withFilters` 가 인자 4개를 받지 않음. 또는 신규 두 테스트가 fail.

### Step 1.4 — `PolicySpecification.withFilters` 시그니처 + EXISTS predicate 구현

- [ ] **`PolicySpecification.java` 의 `withFilters` 메서드 수정**

기존:
```java
public static Specification<Policy> withFilters(RegionFilter regionFilter,
                                                Category category,
                                                PolicyStatus status) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        if (regionFilter != null && regionFilter.isActive()) {
            predicates.add(regionPredicate(root, cb, regionFilter));
        }
        if (category != null) {
            predicates.add(cb.equal(root.get("category"), category));
        }
        if (status != null) {
            predicates.add(cb.equal(effectiveStatusExpr(root, cb), status.name()));
        }

        applyOrder(root, query, cb, status);

        return cb.and(predicates.toArray(new Predicate[0]));
    };
}
```

변경 후:
```java
public static Specification<Policy> withFilters(RegionFilter regionFilter,
                                                Category category,
                                                PolicyStatus status,
                                                SourceType source) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        if (regionFilter != null && regionFilter.isActive()) {
            predicates.add(regionPredicate(root, cb, regionFilter));
        }
        if (category != null) {
            predicates.add(cb.equal(root.get("category"), category));
        }
        if (status != null) {
            predicates.add(cb.equal(effectiveStatusExpr(root, cb), status.name()));
        }
        if (source != null) {
            predicates.add(sourcePredicate(root, query, cb, source));
        }

        applyOrder(root, query, cb, status);

        return cb.and(predicates.toArray(new Predicate[0]));
    };
}

private static Predicate sourcePredicate(Root<Policy> root,
                                          CriteriaQuery<?> query,
                                          CriteriaBuilder cb,
                                          SourceType source) {
    Subquery<Long> sub = query.subquery(Long.class);
    Root<PolicySource> sourceRoot = sub.from(PolicySource.class);
    sub.select(cb.literal(1L))
       .where(
           cb.equal(sourceRoot.get("policy").get("id"), root.get("id")),
           cb.equal(sourceRoot.get("sourceType"), source)
       );
    return cb.exists(sub);
}
```

추가 import:
```java
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import jakarta.persistence.criteria.Subquery;
```

### Step 1.5 — 테스트 실행해서 통과 확인

- [ ] **명령:**
```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicySpecificationTest"
```

기대: 모든 PolicySpecification 테스트 PASS.

### Step 1.6 — Commit

- [ ] **명령:**
```bash
cd backend && git add \
  src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java \
  src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationTest.java
git commit -m "feat(policy): add sourceType filter to PolicySpecification

withFilters 시그니처에 SourceType 인자 추가. EXISTS 서브쿼리로
policy_source 테이블과 매칭하는 정책만 반환."
```

---

## Task 2: Backend — Repository 시그니처 확장

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java`

### Step 2.1 — `PolicyRepository.findAllByFilters` 시그니처에 source 추가

- [ ] **`PolicyRepository.java` 의 메서드 시그니처 수정**

기존:
```java
Page<Policy> findAllByFilters(RegionFilter regionFilter, Category category, PolicyStatus status,
                              Pageable pageable);
```

변경 후:
```java
Page<Policy> findAllByFilters(RegionFilter regionFilter, Category category, PolicyStatus status,
                              SourceType source, Pageable pageable);
```

추가 import:
```java
import com.youthfit.policy.domain.model.SourceType;
```

### Step 2.2 — `PolicyRepositoryImpl.findAllByFilters` 구현 수정

- [ ] **`PolicyRepositoryImpl.java` 의 메서드 수정**

기존:
```java
@Override
public Page<Policy> findAllByFilters(RegionFilter regionFilter, Category category, PolicyStatus status,
                                     Pageable pageable) {
    return jpaRepository.findAll(
            PolicySpecification.withFilters(regionFilter, category, status), pageable);
}
```

변경 후:
```java
@Override
public Page<Policy> findAllByFilters(RegionFilter regionFilter, Category category, PolicyStatus status,
                                     SourceType source, Pageable pageable) {
    return jpaRepository.findAll(
            PolicySpecification.withFilters(regionFilter, category, status, source), pageable);
}
```

추가 import:
```java
import com.youthfit.policy.domain.model.SourceType;
```

### Step 2.3 — 빌드 확인 (이 시점에서 PolicyQueryService 가 깨짐을 예상)

- [ ] **명령:**
```bash
cd backend && ./gradlew compileJava
```

기대: `PolicyQueryService` 에서 `findAllByFilters` 호출이 4-인자 시그니처와 안 맞아 컴파일 실패. 정상. 다음 태스크에서 수정.

### Step 2.4 — 일단 여기까지 커밋하지 않고 Task 3 으로 이어간다

- [ ] Task 3 의 service 수정까지 마치고 한 번에 커밋한다. (repository 만 바뀐 상태는 빌드가 깨져서 의미 없는 commit.)

---

## Task 3: Backend — PolicyQueryService 시그니처 확장

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java`

### Step 3.1 — `PolicyQueryService.findPoliciesByFilters` 시그니처 수정

- [ ] **`PolicyQueryService.java` 의 메서드 수정**

기존:
```java
public PolicyPageResult findPoliciesByFilters(RegionFilter regionFilter, Category category,
                                              PolicyStatus status,
                                              int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<Policy> policyPage = policyRepository.findAllByFilters(regionFilter, category, status, pageable);
    return toPageResult(policyPage);
}
```

변경 후:
```java
public PolicyPageResult findPoliciesByFilters(RegionFilter regionFilter, Category category,
                                              PolicyStatus status, SourceType source,
                                              int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<Policy> policyPage = policyRepository.findAllByFilters(regionFilter, category, status, source, pageable);
    return toPageResult(policyPage);
}
```

추가 import:
```java
import com.youthfit.policy.domain.model.SourceType;
```

### Step 3.2 — `PolicyQueryServiceTest.java` 의 모든 `findPoliciesByFilters` 호출 수정

- [ ] **테스트 파일에서 `findPoliciesByFilters` 호출에 `null` (또는 임의 SourceType) 인자 추가**

기존 호출 형태에 따라 작업한다. 호출이 `service.findPoliciesByFilters(filter, category, status, page, size)` 면 `service.findPoliciesByFilters(filter, category, status, null, page, size)` 로 변경. mock `given(policyRepository.findAllByFilters(...))` 도 인자가 5개에서 6개로 늘었으므로 동일 위치에 `any(SourceType.class)` 또는 `isNull()` 추가.

작업 단계:
1. 파일 열기: `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java`
2. 검색: `findPoliciesByFilters(`
3. 각 호출의 인자 리스트에서 `status` 와 `page` 사이 (즉, `PolicyStatus` 다음, `int page` 앞) 에 `null` (또는 mock 검증용 `any(SourceType.class)`) 삽입.
4. 검색: `findAllByFilters(`
5. 각 호출도 동일 위치에 인자 하나 추가.

추가 import 필요 시:
```java
import com.youthfit.policy.domain.model.SourceType;
```

### Step 3.3 — source 전달 회귀 테스트 추가

- [ ] **`PolicyQueryServiceTest.java` 에 다음 테스트 추가**

```java
@Test
@DisplayName("findPoliciesByFilters: source 인자를 그대로 repository 로 전달한다")
void findPoliciesByFilters_passesSourceToRepository() {
    // given
    PageImpl<Policy> emptyPage = new PageImpl<>(List.of());
    given(policyRepository.findAllByFilters(
            any(RegionFilter.class), any(), any(),
            eq(SourceType.YOUTH_CENTER), any(Pageable.class)))
            .willReturn(emptyPage);

    // when
    PolicyPageResult result = service.findPoliciesByFilters(
            RegionFilter.of(null), null, null,
            SourceType.YOUTH_CENTER, 0, 20);

    // then
    then(policyRepository).should().findAllByFilters(
            any(RegionFilter.class), any(), any(),
            eq(SourceType.YOUTH_CENTER), any(Pageable.class));
    assertThat(result.totalCount()).isZero();
}
```

이 테스트가 추가하는 import 가 기존 파일에 없으면 추가:
```java
import com.youthfit.policy.domain.model.SourceType;
import org.springframework.data.domain.PageImpl;
import static org.mockito.ArgumentMatchers.eq;
```

### Step 3.4 — 테스트 실행해서 통과 확인

- [ ] **명령:**
```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.PolicyQueryServiceTest"
```

기대: PASS.

### Step 3.5 — 백엔드 컴파일 확인 (Controller 는 아직 안 고쳤으니 깨짐 정상)

- [ ] **명령:**
```bash
cd backend && ./gradlew compileJava
```

기대: `PolicyController.findPoliciesByFilters(...)` 호출 인자 부족 컴파일 실패. 정상. Task 4 에서 마무리.

### Step 3.6 — Task 2 + Task 3 까지 한 번에 커밋

- [ ] **명령:**
```bash
cd backend && git add \
  src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java \
  src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java \
  src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java \
  src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java
git commit -m "feat(policy): propagate sourceType filter through repository and service"
```

---

## Task 4: Backend — Controller + API 인터페이스 + MockMvc 테스트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyApi.java`
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java`
- Modify: `backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyControllerTest.java`

### Step 4.1 — `PolicyControllerTest` 기존 mock 호출에 인자 추가 (시그니처 변경 빌드 실패 방지)

- [ ] **파일 검색: `findPoliciesByFilters(`**

기존:
```java
given(policyQueryService.findPoliciesByFilters(any(RegionFilter.class), any(), any(), anyInt(), anyInt()))
        .willReturn(pageResult);
```

변경 후:
```java
given(policyQueryService.findPoliciesByFilters(any(RegionFilter.class), any(), any(), any(), anyInt(), anyInt()))
        .willReturn(pageResult);
```

파일 안 모든 `findPoliciesByFilters` 호출(given/then 양쪽) 에 동일 적용. then 의 verify 호출도 마찬가지:
```java
then(policyQueryService).should()
        .findPoliciesByFilters(any(RegionFilter.class), eq(Category.JOBS), eq(PolicyStatus.OPEN),
                                isNull(), eq(0), eq(10));
```

추가 import:
```java
import com.youthfit.policy.domain.model.SourceType;
import static org.mockito.ArgumentMatchers.isNull;
```

### Step 4.2 — `?source=YOUTH_CENTER` 케이스 신규 테스트 추가

- [ ] **`PolicyControllerTest.java` 에 다음 메서드 추가**

```java
@Test
@DisplayName("GET /api/v1/policies - source 파라미터를 service 로 전달한다")
void findPolicies_withSource_passesSourceTypeToService() throws Exception {
    // given
    PolicyPageResult pageResult = new PolicyPageResult(List.of(), 0L, 0, 20, 0, false);
    given(policyQueryService.findPoliciesByFilters(
            any(RegionFilter.class), any(), any(), eq(SourceType.YOUTH_CENTER), anyInt(), anyInt()))
            .willReturn(pageResult);

    // when & then
    mockMvc.perform(get("/api/v1/policies")
                    .param("source", "YOUTH_CENTER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

    then(policyQueryService).should()
            .findPoliciesByFilters(any(RegionFilter.class), any(), any(),
                    eq(SourceType.YOUTH_CENTER), anyInt(), anyInt());
}

@Test
@DisplayName("GET /api/v1/policies - 잘못된 source enum은 400을 반환한다")
void findPolicies_withInvalidSource_returns400() throws Exception {
    mockMvc.perform(get("/api/v1/policies").param("source", "INVALID_VALUE"))
            .andExpect(status().isBadRequest());
}
```

추가 import 확인:
```java
import static org.mockito.ArgumentMatchers.eq;
```

### Step 4.3 — 테스트 실행해서 실패 확인

- [ ] **명령:**
```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.presentation.controller.PolicyControllerTest"
```

기대: 컴파일은 통과해야 함 (Step 4.1 로 mock 호출 시그니처가 맞춰짐). 새로 추가한 두 테스트는 FAIL — Controller 가 아직 `source` 파라미터를 받지 않음.

### Step 4.4 — `PolicyApi.findPolicies` 시그니처 + Swagger 어노테이션 수정

- [ ] **`PolicyApi.java` 에서 `findPolicies` 메서드 시그니처 변경**

```java
import com.youthfit.policy.domain.model.SourceType;
import io.swagger.v3.oas.annotations.Parameter;

// ... 클래스 내부, findPolicies 메서드 부분

ResponseEntity<PolicyPageResponse> findPolicies(
        @Parameter(description = "행정 코드 CSV (예: 11,26)") String regions,
        @Parameter(description = "(deprecated) 단일 지역 코드") String regionCode,
        @Parameter(description = "카테고리") Category category,
        @Parameter(description = "정책 상태 (OPEN, UPCOMING, CLOSED)") PolicyStatus status,
        @Parameter(description = "출처 (YOUTH_SEOUL_CRAWL, BOKJIRO_CENTRAL, YOUTH_CENTER)") SourceType source,
        @Parameter(description = "0-based page index") int page,
        @Parameter(description = "page size") int size);
```

> 기존 메서드의 `@Parameter` 가 일부만 붙어 있을 수 있다. 이미 붙은 어노테이션은 보존하고, `SourceType source` 만 같은 패턴으로 새로 끼워 넣는다. 파라미터 순서는 `status` 다음, `page` 앞 — Controller 와 일치해야 한다.

### Step 4.5 — `PolicyController.findPolicies` 시그니처 + service 호출 수정

- [ ] **`PolicyController.java` 의 메서드 수정**

기존:
```java
@GetMapping
@Override
public ResponseEntity<PolicyPageResponse> findPolicies(
        @RequestParam(required = false) String regions,
        @RequestParam(required = false) String regionCode,
        @RequestParam(required = false) Category category,
        @RequestParam(required = false) PolicyStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

    RegionFilter filter = resolveRegionFilter(regions, regionCode);
    PolicyPageResult result = policyQueryService.findPoliciesByFilters(
            filter, category, status, page, size);
    return ResponseEntity.ok(PolicyPageResponse.from(result));
}
```

변경 후:
```java
@GetMapping
@Override
public ResponseEntity<PolicyPageResponse> findPolicies(
        @RequestParam(required = false) String regions,
        @RequestParam(required = false) String regionCode,
        @RequestParam(required = false) Category category,
        @RequestParam(required = false) PolicyStatus status,
        @RequestParam(required = false) SourceType source,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

    RegionFilter filter = resolveRegionFilter(regions, regionCode);
    PolicyPageResult result = policyQueryService.findPoliciesByFilters(
            filter, category, status, source, page, size);
    return ResponseEntity.ok(PolicyPageResponse.from(result));
}
```

추가 import:
```java
import com.youthfit.policy.domain.model.SourceType;
```

### Step 4.6 — 테스트 실행해서 모두 통과 확인

- [ ] **명령:**
```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.presentation.controller.PolicyControllerTest"
```

기대: 새 두 테스트 포함 PASS.

### Step 4.7 — 백엔드 전체 테스트 확인

- [ ] **명령:**
```bash
cd backend && ./gradlew test
```

기대: 전체 PASS. 다른 도메인에는 영향 없음.

### Step 4.8 — Commit

- [ ] **명령:**
```bash
cd backend && git add \
  src/main/java/com/youthfit/policy/presentation/controller/PolicyApi.java \
  src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java \
  src/test/java/com/youthfit/policy/presentation/controller/PolicyControllerTest.java
git commit -m "feat(policy): expose ?source= query param on GET /api/v1/policies"
```

---

## Task 5: Frontend — Types + API client

**Files:**
- Modify: `frontend/src/types/policy.ts`
- Modify: `frontend/src/apis/policy.api.ts`

### Step 5.1 — `SOURCE_LABELS` Record + `isSourceType` 가드 추가

- [ ] **`frontend/src/types/policy.ts` 의 `SourceType` 정의 근처에 추가**

기존:
```typescript
export type SourceType = 'YOUTH_SEOUL_CRAWL' | 'BOKJIRO_CENTRAL' | 'YOUTH_CENTER';
```

바로 아래에 추가:
```typescript
export const SOURCE_LABELS: Record<SourceType, string> = {
  YOUTH_CENTER: '온통청년',
  BOKJIRO_CENTRAL: '복지로',
  YOUTH_SEOUL_CRAWL: '청년서울',
};

const SOURCE_TYPE_VALUES: readonly SourceType[] = [
  'YOUTH_CENTER',
  'BOKJIRO_CENTRAL',
  'YOUTH_SEOUL_CRAWL',
];

export function isSourceType(value: string | null): value is SourceType {
  return value !== null && (SOURCE_TYPE_VALUES as readonly string[]).includes(value);
}
```

### Step 5.2 — `fetchPolicies` 에 `source` 파라미터 추가

- [ ] **`frontend/src/apis/policy.api.ts` 수정**

`PolicyListParams` 인터페이스:
```typescript
interface PolicyListParams {
  category?: string;
  source?: SourceType;       // NEW
  regions?: string[];
  regionCode?: string;
  status?: PolicyStatus;
  page?: number;
  size?: number;
}
```

`fetchPolicies` 함수 안:
```typescript
export async function fetchPolicies(params: PolicyListParams): Promise<PolicyPage> {
  const searchParams = new URLSearchParams();
  if (params.category) searchParams.set('category', params.category);
  if (params.source) searchParams.set('source', params.source);     // NEW
  if (params.regions && params.regions.length > 0) {
    searchParams.set('regions', params.regions.join(','));
  } else if (params.regionCode) {
    searchParams.set('regionCode', params.regionCode);
  }
  if (params.status) searchParams.set('status', params.status);
  searchParams.set('page', String(params.page ?? 0));
  searchParams.set('size', String(params.size ?? 20));

  return api.get('v1/policies', { searchParams }).json<PolicyPage>();
}
```

import 에 `SourceType` 추가:
```typescript
import type {
  PolicyPage,
  PolicyDetail,
  PolicyStatus,
  SourceType,
  PolicyCalendarResponse,
  PolicyCalendarPageResponse,
} from '@/types/policy';
```

### Step 5.3 — 타입체크

- [ ] **명령:**
```bash
cd frontend && npm run build
```

기대: 빌드 성공. (아직 호출처가 source 를 전달하지 않으니 옵셔널이라 통과.)

### Step 5.4 — Commit

- [ ] **명령:**
```bash
cd frontend && git add src/types/policy.ts src/apis/policy.api.ts
git commit -m "feat(policy): add SOURCE_LABELS, isSourceType, source param to fetchPolicies"
```

---

## Task 6: Frontend — usePolicies 훅

**Files:**
- Modify: `frontend/src/hooks/queries/usePolicies.ts`

### Step 6.1 — `UsePoliciesParams` 와 queryKey 에 source 추가

- [ ] **`usePolicies.ts` 전체 교체**

```typescript
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { fetchPolicies, searchPolicies } from '@/apis/policy.api';
import type { PolicyCategory, PolicyStatus, SourceType } from '@/types/policy';

interface UsePoliciesParams {
  keyword?: string;
  category?: PolicyCategory | '';
  source?: SourceType | '';
  status?: PolicyStatus | '';
  regions?: string[];
  page?: number;
  size?: number;
}

export function usePolicies(params: UsePoliciesParams) {
  const { keyword, category, source, status, regions, page = 0, size = 6 } = params;
  const regionsKey = regions && regions.length > 0 ? regions.join(',') : '';

  return useQuery({
    queryKey: ['policies', { keyword, category, source, status, regions: regionsKey, page, size }],
    queryFn: () =>
      keyword
        ? searchPolicies(keyword, { status: status || undefined, page, size })
        : fetchPolicies({
            category: category || undefined,
            source: source || undefined,
            status: status || undefined,
            regions: regions && regions.length > 0 ? regions : undefined,
            page,
            size,
          }),
    placeholderData: keepPreviousData,
  });
}
```

### Step 6.2 — 타입체크

- [ ] **명령:**
```bash
cd frontend && npm run build
```

기대: 빌드 성공.

### Step 6.3 — Commit

- [ ] **명령:**
```bash
cd frontend && git add src/hooks/queries/usePolicies.ts
git commit -m "feat(policy): accept source param in usePolicies hook"
```

---

## Task 7: Frontend — PolicyFilterBar 출처 chip group

**Files:**
- Modify: `frontend/src/components/policy/PolicyFilterBar.tsx`

### Step 7.1 — `PolicyFilterBar` props, 데스크톱 라인, 모바일 시트 모두 출처 칩 추가

- [ ] **`PolicyFilterBar.tsx` 전체 교체** (`SOURCE_LABELS` 와 props 변경, 두 위치에 chip group 추가)

```tsx
import { useState, useEffect } from 'react';
import { SlidersHorizontal, X } from 'lucide-react';
import { cn } from '@/lib/cn';
import RegionPicker from '@/components/policy/RegionPicker';
import RegionPickerTrigger from '@/components/policy/RegionPickerTrigger';
import type { PolicyCategory, SourceType } from '@/types/policy';
import { CATEGORY_LABELS, SOURCE_LABELS } from '@/types/policy';
import type { RegionListResponse } from '@/types/region';

const CATEGORY_ENTRIES = Object.entries(CATEGORY_LABELS) as [PolicyCategory, string][];
const SOURCE_ENTRIES: [SourceType, string][] = [
  ['YOUTH_CENTER', SOURCE_LABELS.YOUTH_CENTER],
  ['BOKJIRO_CENTRAL', SOURCE_LABELS.BOKJIRO_CENTRAL],
  ['YOUTH_SEOUL_CRAWL', SOURCE_LABELS.YOUTH_SEOUL_CRAWL],
];

type Props = {
  category: PolicyCategory | '';
  source: SourceType | '';
  regions: string[];
  regionData: RegionListResponse | undefined;
  onCategoryChange: (next: PolicyCategory | '') => void;
  onSourceChange: (next: SourceType | '') => void;
  onRegionsChange: (codes: string[]) => void;
  disabled?: boolean;
  disabledHint?: string;
};

/* ──────────────────────────────────────────────
   MobileFilterSheet (내부 전용)
   ────────────────────────────────────────────── */

function MobileFilterSheet({
  isOpen,
  onClose,
  category,
  source,
  onCategoryChange,
  onSourceChange,
}: {
  isOpen: boolean;
  onClose: () => void;
  category: PolicyCategory | '';
  source: SourceType | '';
  onCategoryChange: (v: PolicyCategory | '') => void;
  onSourceChange: (v: SourceType | '') => void;
}) {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
        aria-hidden="true"
      />
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
            <button
              onClick={() => onCategoryChange('')}
              className={cn(
                'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
                category === ''
                  ? 'border-transparent bg-brand-100 text-indigo-600'
                  : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
              )}
            >
              전체
            </button>
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

        <fieldset className="mt-6">
          <legend className="mb-2 text-sm font-semibold text-gray-700">제공 출처</legend>
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => onSourceChange('')}
              className={cn(
                'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
                source === ''
                  ? 'border-transparent bg-brand-100 text-indigo-600'
                  : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
              )}
            >
              전체 출처
            </button>
            {SOURCE_ENTRIES.map(([key, label]) => (
              <button
                key={key}
                onClick={() => onSourceChange(source === key ? '' : key)}
                className={cn(
                  'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
                  source === key
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

/* ──────────────────────────────────────────────
   PolicyFilterBar
   ────────────────────────────────────────────── */

export default function PolicyFilterBar({
  category,
  source,
  regions,
  regionData,
  onCategoryChange,
  onSourceChange,
  onRegionsChange,
  disabled = false,
  disabledHint,
}: Props) {
  const [sheetOpen, setSheetOpen] = useState(false);
  const [regionPickerOpen, setRegionPickerOpen] = useState(false);

  const activeFilterCount = (category ? 1 : 0) + (source ? 1 : 0);

  return (
    <>
      {/* ── Desktop Filters ── */}
      <div className="mb-4 hidden flex-wrap items-center gap-2 md:flex">
        <button
          onClick={() => onCategoryChange('')}
          className={cn(
            'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
            category === ''
              ? 'border-transparent bg-brand-100 text-indigo-600'
              : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
          )}
        >
          전체
        </button>
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

        <button
          onClick={() => onSourceChange('')}
          disabled={disabled}
          className={cn(
            'rounded-full border px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50',
            source === ''
              ? 'border-transparent bg-brand-100 text-indigo-600'
              : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
          )}
        >
          전체 출처
        </button>
        {SOURCE_ENTRIES.map(([key, label]) => (
          <button
            key={key}
            onClick={() => onSourceChange(source === key ? '' : key)}
            disabled={disabled}
            className={cn(
              'rounded-full border px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50',
              source === key
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
          {/* 데스크톱 팝오버 모드 */}
          <div className="hidden md:block">
            <RegionPicker
              open={regionPickerOpen}
              onClose={() => setRegionPickerOpen(false)}
              selectedCodes={regions}
              onApply={onRegionsChange}
              regionData={regionData}
              mode="desktop-popover"
            />
          </div>
        </div>

        {disabled && disabledHint && (
          <span className="ml-2 text-xs text-gray-500">{disabledHint}</span>
        )}
      </div>

      {/* ── Mobile Filter Bar ── */}
      <div className="mb-4 flex flex-wrap items-center gap-2 md:hidden">
        <button
          onClick={() => setSheetOpen(true)}
          className="flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white px-4 py-2 text-sm font-semibold text-neutral-700 transition-colors hover:bg-gray-50"
          aria-label="필터 열기"
        >
          <SlidersHorizontal className="h-4 w-4" />
          필터
          {activeFilterCount > 0 && (
            <span className="ml-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand-800 text-xs text-white">
              {activeFilterCount}
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
          <span className="text-[10px] text-gray-500">검색 결과 미적용</span>
        )}
      </div>

      {/* Mobile category sheet */}
      <MobileFilterSheet
        isOpen={sheetOpen}
        onClose={() => setSheetOpen(false)}
        category={category}
        source={source}
        onCategoryChange={onCategoryChange}
        onSourceChange={onSourceChange}
      />

      {/* 모바일 RegionPicker (sheet) */}
      <div className="md:hidden">
        <RegionPicker
          open={regionPickerOpen}
          onClose={() => setRegionPickerOpen(false)}
          selectedCodes={regions}
          onApply={onRegionsChange}
          regionData={regionData}
          mode="mobile-sheet"
        />
      </div>
    </>
  );
}
```

### Step 7.2 — 타입체크

- [ ] **명령:**
```bash
cd frontend && npm run build
```

기대: `PolicyListPage` 가 `source` / `onSourceChange` prop 을 아직 안 넘겨서 컴파일 실패. 정상. Task 8 에서 마무리.

### Step 7.3 — Task 7 단독 커밋은 하지 않고 Task 8 까지 마치고 한 번에 커밋

- [ ] Task 8 의 PolicyListPage 수정 완료 후 합쳐서 커밋.

---

## Task 8: Frontend — PolicyListPage URL 상태 + 핸들러 + active filter badge

**Files:**
- Modify: `frontend/src/pages/PolicyListPage.tsx`

### Step 8.1 — `source` 읽기·쓰기·활성 배지 통합

- [ ] **`PolicyListPage.tsx` 의 다음 위치 수정**

(1) import 에 `SourceType`, `SOURCE_LABELS`, `isSourceType` 추가:
```typescript
import type {
  PolicyCategory,
  PolicyStatus,
  SourceType,
} from '@/types/policy';
import { CATEGORY_LABELS, SOURCE_LABELS, isSourceType } from '@/types/policy';
```

(2) URL 파라미터 읽기 (`category`, `status`, `regions` 읽는 블록 옆에 추가):
```typescript
const rawSource = searchParams.get('source');
const source: SourceType | '' = isSourceType(rawSource) ? rawSource : '';
```

(3) `usePolicies` 호출에 `source` 전달:
```typescript
const { data, isLoading, isError, refetch } = usePolicies({
  keyword: keyword || undefined,
  category,
  source,
  status,
  regions: regions.length > 0 ? regions : undefined,
  page,
  size: PAGE_SIZE,
});
```

(4) `PolicyFilterBar` 호출에 props 추가:
```tsx
<PolicyFilterBar
  category={category}
  source={source}
  regions={regions}
  regionData={regionData}
  onCategoryChange={(v) => updateParams({ category: v, page: '' })}
  onSourceChange={(v) => updateParams({ source: v, page: '' })}
  onRegionsChange={handleRegionApply}
  disabled={isSearchMode}
  disabledHint="검색 결과에는 지역·출처 필터가 적용되지 않습니다"
/>
```

> `disabledHint` 문구를 "지역·출처" 로 확장.

(5) `activeFilters` 배열에 source 추가:
```typescript
const activeFilters: { key: string; label: string }[] = [];
if (category) activeFilters.push({ key: 'category', label: CATEGORY_LABELS[category] });
if (source) activeFilters.push({ key: 'source', label: SOURCE_LABELS[source] });
```

(6) `hasActiveQuery` 에 source 포함:
```typescript
const hasActiveQuery = Boolean(
  keyword || category || source || regions.length > 0 || status !== DEFAULT_STATUS
);
```

### Step 8.2 — 빌드 + 타입체크 + 테스트

- [ ] **명령:**
```bash
cd frontend && npm run build && npm run test
```

기대: 빌드 성공, 기존 테스트 모두 PASS.

### Step 8.3 — Task 7 + Task 8 함께 커밋

- [ ] **명령:**
```bash
cd frontend && git add \
  src/components/policy/PolicyFilterBar.tsx \
  src/pages/PolicyListPage.tsx
git commit -m "feat(policy): add source filter chips to policy list

데스크톱은 카테고리 칩 우측 구분선 뒤에, 모바일 시트는 카테고리
fieldset 아래 '제공 출처' fieldset 으로 노출. 검색 모드에서는
지역 필터와 동일하게 disabled. URL ?source= 로 상태 보존."
```

---

## Task 9: 수동 검증 (백/프 동시 실행)

**Files:**
- 없음 — 골든 패스 동작 확인만.

### Step 9.1 — 백엔드 실행

- [ ] **명령 (별도 터미널):**
```bash
cd backend && ./gradlew bootRun
```

대기: `Started YouthfitApplication` 메시지 확인.

### Step 9.2 — 프론트엔드 실행

- [ ] **명령 (별도 터미널):**
```bash
cd frontend && npm run dev
```

대기: `http://localhost:5173` 안내. 브라우저에서 접속.

### Step 9.3 — 데스크톱 골든 패스

- [ ] `/policies` 페이지 진입.
- [ ] 데스크톱 폭(>= 768px) 에서 필터 라인에 `전체 출처 / 온통청년 / 복지로 / 청년서울` 4개 칩이 카테고리 칩 우측에 표시되는지 확인.
- [ ] `복지로` 칩 클릭. URL 이 `?source=BOKJIRO_CENTRAL` 로 바뀌고 결과가 갱신되는지 확인.
- [ ] 결과 카드 상단의 `SourceBadge` 가 모두 복지로 로고인지 확인.
- [ ] `복지로` 칩 다시 클릭. URL 에서 `source` 가 사라지고 전체 출처로 돌아오는지 확인.
- [ ] active filter 영역에 `복지로` 배지가 뜨고, `X` 버튼으로 해제되는지 확인.

### Step 9.4 — 모바일 골든 패스

- [ ] 브라우저 폭을 모바일 (375px) 로 줄임.
- [ ] 필터 버튼 탭 → 시트 열림. "카테고리" 아래에 "제공 출처" fieldset 이 보이는지 확인.
- [ ] `온통청년` 선택 → "필터 적용" 탭. 결과가 갱신되고 필터 버튼 배지 카운트가 1 이상으로 표시되는지 확인.
- [ ] 카테고리도 같이 선택 → 배지 카운트가 2 가 되는지 확인.

### Step 9.5 — 검색 모드 disabled 확인

- [ ] 검색창에 키워드 입력 (예: "주거").
- [ ] 데스크톱 출처 칩들이 흐려지고 클릭 불가 상태가 되는지 확인.
- [ ] 안내 문구 "검색 결과에는 지역·출처 필터가 적용되지 않습니다" 가 표시되는지 확인.

### Step 9.6 — Edge case

- [ ] URL 에 `?source=INVALID` 를 직접 입력했을 때 페이지가 깨지지 않고 "전체 출처" 상태로 fallback 되는지 확인 (isSourceType 타입 가드 동작).
- [ ] URL 에 `?source=YOUTH_CENTER&category=HOUSING&regions=11` 처럼 동시 적용. 백엔드 쿼리 결과가 교집합으로 나오는지 확인.

### Step 9.7 — 검증 결과 메모

- [ ] 위 단계 중 실패한 항목이 있으면 별도 fix 커밋. 없으면 이 태스크 완료 후 PR 생성 단계로 넘어간다.

---

## 최종 검증

- [ ] **백엔드 전체 테스트:**
```bash
cd backend && ./gradlew test
```
기대: 전체 PASS.

- [ ] **프론트엔드 전체 테스트 + 빌드:**
```bash
cd frontend && npm run test && npm run build
```
기대: 전체 PASS, 빌드 성공.

- [ ] **변경 파일 요약:**
```bash
git diff --stat origin/main...HEAD
```
기대: Spec 의 "백엔드 6 + 백엔드 테스트 3 + 프론트 5 + spec/plan 2" 범위 안에 들어옴.

- [ ] **PR 생성 (사용자 승인 후):** `create-pr` 스킬 호출하거나 `gh pr create` 사용. 본 plan 은 implementation 끝까지만 다룸.
