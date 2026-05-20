# Policy Region Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 시·도 → 시·군·구 2단계 드릴다운 + 다중 선택 + "전국" 정책 항상 포함 + 프로필 자동 적용을 지원하는 정책 지역 필터를 만든다.

**Architecture:** 백엔드는 (1) 신규 `GET /api/v1/regions` 마스터 API, (2) `RegionFilter` 도메인 값 객체로 매칭 로직을 추출해 `PolicySpecification` 의 콤마 패딩 LIKE 조건을 만든다. 프론트는 `RegionPicker` / `Trigger` / `Banner` 컴포넌트와 `useRegions` 훅으로 picker 를 분리하고, `PolicyListPage` 는 URL `?regions=` CSV 만 다룬다.

**Tech Stack:** Spring Boot 4.0.5, Java 21, JPA Specification, JUnit 5 + Mockito, React 19, TypeScript 5, TanStack Query v5, Vite 6, Vitest, Tailwind 4.

**Spec:** [`docs/superpowers/specs/2026-05-21-policy-region-filter-design.md`](../specs/2026-05-21-policy-region-filter-design.md)

---

## File Structure

### 신규 (백엔드)

| 파일 | 책임 |
|------|------|
| `backend/.../policy/domain/model/RegionFilter.java` | 사용자 입력 코드 CSV → 정규화된 매칭 spec. 순수 도메인 객체. |
| `backend/.../policy/presentation/controller/RegionApi.java` | Swagger 인터페이스 |
| `backend/.../policy/presentation/controller/RegionController.java` | `GET /api/v1/regions` |
| `backend/.../policy/application/service/RegionQueryService.java` | 마스터 데이터 조회 |
| `backend/.../policy/application/dto/result/RegionListResult.java` | Result DTO (record + nested Sido/Sigungu) |
| `backend/.../policy/presentation/dto/response/RegionListResponse.java` | Response DTO |
| `backend/src/test/.../policy/domain/model/RegionFilterTest.java` | RegionFilter 단위 테스트 |
| `backend/src/test/.../policy/presentation/controller/RegionControllerTest.java` | @WebMvcTest |
| `backend/src/test/.../policy/application/service/RegionQueryServiceTest.java` | @ExtendWith(MockitoExtension) |

### 수정 (백엔드)

| 파일 | 변경 |
|------|------|
| `PolicySpecification.java` | `withFilters` 시그니처 + RegionFilter 연동 + 콤마 패딩 LIKE |
| `PolicyRepository.java` | `findAllByFilters(List<String> regionCodes, ...)` |
| `PolicyRepositoryImpl.java` | 시그니처 전파 |
| `PolicyQueryService.java` | `findPoliciesByFilters` 시그니처 |
| `PolicyController.java` | `regions` CSV 파라미터 + legacy `regionCode` 호환 |
| `PolicyApi.java` | Swagger 어노테이션 |
| `PolicySpecificationTest.java` | regions 매칭 smoke 테스트 추가 |
| `PolicyQueryServiceTest.java` | 시그니처 변경 반영 |
| `PolicyControllerTest.java` | 파라미터 변경 반영 + 호환 케이스 |

### 신규 (프론트엔드)

| 파일 | 책임 |
|------|------|
| `frontend/src/apis/region.api.ts` | `fetchRegions()` |
| `frontend/src/hooks/queries/useRegions.ts` | useQuery 래퍼 |
| `frontend/src/components/policy/RegionPicker.tsx` | 모바일 시트 / 데스크톱 팝오버 본체 |
| `frontend/src/components/policy/RegionPickerTrigger.tsx` | 칩 트리거 |
| `frontend/src/components/policy/RegionPickerBanner.tsx` | "내 지역" 자동 적용 배너 |
| `frontend/src/types/region.ts` | RegionListResponse 클라이언트 타입 |
| `frontend/src/lib/regionFilter.ts` | URL CSV ↔ string[] 헬퍼, sido/sigungu 분류 |
| `frontend/src/pages/_dev/RegionPickerPlayground.tsx` | 임시 미리보기 (Task 16에서 삭제) |
| `frontend/src/components/policy/RegionPickerBanner.test.tsx` | 단위 테스트 |
| `frontend/src/lib/regionFilter.test.ts` | 단위 테스트 |
| `frontend/src/lib/labels/region.test.ts` | SIDO_CODE_BY_ENUM 매핑 테스트 |

### 수정 (프론트엔드)

| 파일 | 변경 |
|------|------|
| `frontend/src/lib/labels/region.ts` | `SIDO_CODE_BY_ENUM` 매핑 추가 |
| `frontend/src/apis/policy.api.ts` | `regions: string[]` 파라미터 |
| `frontend/src/hooks/queries/usePolicies.ts` | `regions` 인자 + queryKey |
| `frontend/src/pages/PolicyListPage.tsx` | 지역 select 제거, picker 통합, 자동 적용 effect |
| `frontend/src/components/policy/PolicyCard.tsx` | 지역 뱃지 |
| `frontend/src/App.tsx` | `/_dev/region-picker` 라우트 (Task 13 추가, Task 16 삭제) |

---

## Pre-flight

### Task 0: Baseline 확인

**목적:** 시작 시점에 기존 테스트·빌드가 정상인지 확인하여, 이후 실패는 모두 본 작업 때문임을 보장한다.

- [ ] **Step 1: 백엔드 테스트 전체 실행**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend
./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 프론트엔드 빌드 + 테스트**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/frontend
npm run build
npm run test
```
Expected: 둘 다 통과

- [ ] **Step 3: 작업 브랜치 생성**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git checkout -b feat/policy-region-filter
```

---

## Phase 1 — Backend

### Task 1: RegionFilter 도메인 값 객체 (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/RegionFilter.java`
- Test: `backend/src/test/java/com/youthfit/policy/domain/model/RegionFilterTest.java`

**책임:** 사용자 입력(코드 CSV) 을 받아서 정규화한다. `Policy.regionCode` / `Policy.regionCodes` 매칭에 쓸 spec 을 만들어준다. JPA 의존성이 전혀 없는 순수 객체.

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/policy/domain/model/RegionFilterTest.java`:

```java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegionFilter")
class RegionFilterTest {

    @Nested
    @DisplayName("of(codes)")
    class Of {
        @Test
        @DisplayName("null 입력은 비활성 필터를 반환한다 — 전체 정책 조회")
        void nullCodes_returnsInactive() {
            RegionFilter filter = RegionFilter.of(null);
            assertThat(filter.isActive()).isFalse();
        }

        @Test
        @DisplayName("빈 리스트도 비활성")
        void emptyCodes_returnsInactive() {
            assertThat(RegionFilter.of(List.of()).isActive()).isFalse();
        }

        @Test
        @DisplayName("NATIONWIDE 단독 — 전국만 보기 모드")
        void nationwideAlone_returnsNationwideOnly() {
            RegionFilter filter = RegionFilter.of(List.of("NATIONWIDE"));
            assertThat(filter.isActive()).isTrue();
            assertThat(filter.isNationwideOnly()).isTrue();
            assertThat(filter.sidoCodes()).isEmpty();
            assertThat(filter.sigunguCodes()).isEmpty();
        }

        @Test
        @DisplayName("한글 별칭 '전국' 도 NATIONWIDE 와 같이 인식한다")
        void koreanNationwide_treatedSame() {
            assertThat(RegionFilter.of(List.of("전국")).isNationwideOnly()).isTrue();
        }

        @Test
        @DisplayName("2자리 코드는 시·도로 분류된다")
        void twoDigitCode_classifiedAsSido() {
            RegionFilter filter = RegionFilter.of(List.of("11"));
            assertThat(filter.sidoCodes()).containsExactly("11");
            assertThat(filter.sigunguCodes()).isEmpty();
            assertThat(filter.isNationwideOnly()).isFalse();
        }

        @Test
        @DisplayName("5자리 코드는 시·군·구로 분류된다")
        void fiveDigitCode_classifiedAsSigungu() {
            RegionFilter filter = RegionFilter.of(List.of("11680"));
            assertThat(filter.sigunguCodes()).containsExactly("11680");
            assertThat(filter.sidoCodes()).isEmpty();
        }

        @Test
        @DisplayName("혼합 입력은 각각 분류된다")
        void mixed_classifiedSeparately() {
            RegionFilter filter = RegionFilter.of(List.of("11", "26260", "41"));
            assertThat(filter.sidoCodes()).containsExactly("11", "41");
            assertThat(filter.sigunguCodes()).containsExactly("26260");
        }

        @Test
        @DisplayName("알 수 없는 길이의 코드는 무시한다")
        void invalidLength_ignored() {
            RegionFilter filter = RegionFilter.of(List.of("1", "123", "1234567"));
            assertThat(filter.isActive()).isFalse();
        }

        @Test
        @DisplayName("숫자 아닌 코드는 무시한다 (NATIONWIDE 토큰 제외)")
        void nonDigit_ignored() {
            RegionFilter filter = RegionFilter.of(List.of("ABC", "11680"));
            assertThat(filter.sigunguCodes()).containsExactly("11680");
            assertThat(filter.sidoCodes()).isEmpty();
        }

        @Test
        @DisplayName("중복 코드는 1회만 반영한다")
        void duplicates_deduplicated() {
            RegionFilter filter = RegionFilter.of(List.of("11", "11", "11680", "11680"));
            assertThat(filter.sidoCodes()).containsExactly("11");
            assertThat(filter.sigunguCodes()).containsExactly("11680");
        }

        @Test
        @DisplayName("공백·null 항목은 무시한다")
        void blankItems_ignored() {
            java.util.List<String> input = new java.util.ArrayList<>();
            input.add("  11 ");
            input.add(null);
            input.add("");
            RegionFilter filter = RegionFilter.of(input);
            assertThat(filter.sidoCodes()).containsExactly("11");
        }

        @Test
        @DisplayName("NATIONWIDE 가 다른 코드와 함께 오면 일반 필터로 취급 (전국만 모드 아님)")
        void nationwideWithOthers_notNationwideOnly() {
            RegionFilter filter = RegionFilter.of(List.of("NATIONWIDE", "11"));
            assertThat(filter.isNationwideOnly()).isFalse();
            assertThat(filter.sidoCodes()).containsExactly("11");
        }
    }

    @Nested
    @DisplayName("ofCsv(csv)")
    class OfCsv {
        @Test
        @DisplayName("CSV 문자열을 파싱한다")
        void parsesCsv() {
            RegionFilter filter = RegionFilter.ofCsv("11,11680,26260");
            assertThat(filter.sidoCodes()).containsExactly("11");
            assertThat(filter.sigunguCodes()).containsExactly("11680", "26260");
        }

        @Test
        @DisplayName("null 또는 blank CSV 는 비활성")
        void nullOrBlank_inactive() {
            assertThat(RegionFilter.ofCsv(null).isActive()).isFalse();
            assertThat(RegionFilter.ofCsv("").isActive()).isFalse();
            assertThat(RegionFilter.ofCsv("   ").isActive()).isFalse();
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend
./gradlew test --tests "com.youthfit.policy.domain.model.RegionFilterTest"
```
Expected: COMPILATION FAIL — `RegionFilter` 클래스 없음

- [ ] **Step 3: RegionFilter 구현**

`backend/src/main/java/com/youthfit/policy/domain/model/RegionFilter.java`:

```java
package com.youthfit.policy.domain.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RegionFilter {

    public static final String NATIONWIDE_TOKEN = "NATIONWIDE";
    private static final String NATIONWIDE_KOREAN = "전국";

    private final boolean nationwideOnly;
    private final List<String> sidoCodes;
    private final List<String> sigunguCodes;

    private RegionFilter(boolean nationwideOnly, List<String> sidoCodes, List<String> sigunguCodes) {
        this.nationwideOnly = nationwideOnly;
        this.sidoCodes = List.copyOf(sidoCodes);
        this.sigunguCodes = List.copyOf(sigunguCodes);
    }

    public static RegionFilter of(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return inactive();
        }
        Set<String> seen = new LinkedHashSet<>();
        boolean hasNationwideToken = false;
        List<String> sidos = new ArrayList<>();
        List<String> sigungus = new ArrayList<>();

        for (String raw : codes) {
            if (raw == null) continue;
            String code = raw.trim();
            if (code.isEmpty()) continue;
            if (!seen.add(code)) continue;

            if (NATIONWIDE_TOKEN.equals(code) || NATIONWIDE_KOREAN.equals(code)) {
                hasNationwideToken = true;
                continue;
            }
            if (!code.chars().allMatch(Character::isDigit)) continue;
            if (code.length() == 2) sidos.add(code);
            else if (code.length() == 5) sigungus.add(code);
            // 다른 길이는 무시
        }

        boolean nationwideOnly = hasNationwideToken && sidos.isEmpty() && sigungus.isEmpty();
        if (!hasNationwideToken && sidos.isEmpty() && sigungus.isEmpty()) {
            return inactive();
        }
        return new RegionFilter(nationwideOnly, sidos, sigungus);
    }

    public static RegionFilter ofCsv(String csv) {
        if (csv == null || csv.isBlank()) return inactive();
        return of(Arrays.asList(csv.split(",")));
    }

    private static RegionFilter inactive() {
        return new RegionFilter(false, List.of(), List.of());
    }

    public boolean isActive() {
        return nationwideOnly || !sidoCodes.isEmpty() || !sigunguCodes.isEmpty();
    }

    public boolean isNationwideOnly() {
        return nationwideOnly;
    }

    public List<String> sidoCodes() {
        return sidoCodes;
    }

    public List<String> sigunguCodes() {
        return sigunguCodes;
    }
}
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew test --tests "com.youthfit.policy.domain.model.RegionFilterTest"
```
Expected: PASS (12개 테스트)

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/policy/domain/model/RegionFilter.java \
        backend/src/test/java/com/youthfit/policy/domain/model/RegionFilterTest.java
git commit -m "$(cat <<'EOF'
feat(policy): RegionFilter 도메인 값 객체 추가

사용자 입력 코드 리스트를 시·도/시·군·구/전국 모드로 정규화하는
순수 도메인 객체. JPA 의존성 없이 단위 테스트 가능.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: PolicySpecification — RegionFilter 연동 및 콤마 패딩 LIKE

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java`
- Modify: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationTest.java`

**책임:** `withFilters` 시그니처를 `RegionFilter` 로 바꾸고, 콤마 패딩 LIKE 로 false-positive 를 피하며 spec §4.5 매칭 규칙을 구현한다. CSV 칼럼 매칭 시 `','||region_codes||','` 형태로 양 끝을 콤마로 감싼 후 `LIKE '%,Cxxxxx,%'` 검사.

- [ ] **Step 1: 기존 테스트 시그니처 변경 — 실패 유도**

`backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationTest.java` 의 기존 호출부 `PolicySpecification.withFilters(regionCode, category, status)` 를 `PolicySpecification.withFilters(RegionFilter.of(...), category, status)` 로 모두 변경. (테스트 본체는 그대로 두고 시그니처만.)

기존 `regionCode != null` 케이스가 있다면:
```java
// Before
PolicySpecification.withFilters("11", null, null)
// After
PolicySpecification.withFilters(RegionFilter.of(java.util.List.of("11")), null, null)
```

`regionCode == null` 케이스는 `RegionFilter.of(null)` 로 변경. import 추가:
```java
import com.youthfit.policy.domain.model.RegionFilter;
```

- [ ] **Step 2: 새 매칭 테스트 케이스 추가**

`PolicySpecificationTest` 클래스 안에 `@Nested` 블록 추가:

```java
@Nested
@DisplayName("withFilters(regionFilter) — 매칭 분기")
class RegionMatching {

    @Test
    @DisplayName("비활성 필터: 지역 관련 predicate 가 추가되지 않는다 (status/category 만)")
    void inactiveFilter_noRegionPredicate() {
        // given
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
        PolicySpecification.withFilters(RegionFilter.of(null), null, null).toPredicate(root, query, cb);

        // then — region 쪽 cb.or 가 호출되지 않음을 확인하기 위해, 적어도 그 분기에 진입했을 때만 사용하는 메서드(cb.concat) 호출 회수가 0 이어야 한다
        then(cb).should(times(0)).concat(anyString(), any(jakarta.persistence.criteria.Expression.class));
    }

    @Test
    @DisplayName("NATIONWIDE 단독: regionCode == '전국' predicate 만 추가된다")
    void nationwideOnly_addsEqualNationwidePredicate() {
        // given
        Predicate eq = mock(Predicate.class);
        given(cb.equal(any(), anyString())).willReturn(eq);
        given(cb.and(any(Predicate[].class))).willReturn(eq);
        Path<Object> path = mock(Path.class);
        given(root.get("regionCode")).willReturn(path);

        // when
        PolicySpecification.withFilters(
                RegionFilter.of(java.util.List.of("NATIONWIDE")), null, null)
                .toPredicate(root, query, cb);

        // then — cb.equal(regionCode, "전국") 이 호출되었음을 검증
        then(cb).should().equal(eq(path), eq("전국"));
    }

    @Test
    @DisplayName("시·도 코드: regionCode 정확/prefix 매칭 + regionCodes CSV LIKE 가 모두 OR 로 추가된다")
    void sidoCode_addsMultiplePredicates() {
        // given
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
                RegionFilter.of(java.util.List.of("11")), null, null)
                .toPredicate(root, query, cb);

        // then — like 가 시·도 prefix(11%) + CSV 패딩(",11," ",11xxx,") 으로 최소 3회 이상 호출됨
        then(cb).should(atLeast(3)).like(any(jakarta.persistence.criteria.Expression.class), anyString());
    }

    @Test
    @DisplayName("시·군·구 코드: regionCode 정확 매칭 + CSV 콤마 패딩 LIKE 가 추가된다")
    void sigunguCode_addsExactAndCsvLike() {
        // given
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
                RegionFilter.of(java.util.List.of("11680")), null, null)
                .toPredicate(root, query, cb);

        // then — 콤마 패딩 LIKE %,11680,% 가 호출되어야 함
        then(cb).should().like(any(jakarta.persistence.criteria.Expression.class), eq("%,11680,%"));
    }
}
```

추가 import (이미 있는 것 외):
```java
import com.youthfit.policy.domain.model.RegionFilter;
import static org.mockito.Mockito.atLeast;
```

- [ ] **Step 3: 실패 확인**

```bash
./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicySpecificationTest"
```
Expected: COMPILATION FAIL — `withFilters` 시그니처 불일치

- [ ] **Step 4: PolicySpecification 구현 교체**

`backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java` 전체 교체:

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PolicySpecification {

    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);
    private static final LocalDate FAR_PAST = LocalDate.of(1, 1, 1);
    private static final String NATIONWIDE_LABEL = "전국";

    private PolicySpecification() {
    }

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

    public static Specification<Policy> withKeyword(String keyword, PolicyStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String pattern = "%" + keyword.toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("summary")), pattern)
            ));
            if (status != null) {
                predicates.add(cb.equal(effectiveStatusExpr(root, cb), status.name()));
            }

            applyOrder(root, query, cb, status);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate regionPredicate(Root<Policy> root, CriteriaBuilder cb, RegionFilter f) {
        Path<String> regionCode = root.get("regionCode");
        // 콤마 패딩된 CSV 표현: ',' || region_codes || ','
        Expression<String> paddedCsv = cb.concat(cb.concat(",", root.<String>get("regionCodes")), ",");

        if (f.isNationwideOnly()) {
            return cb.equal(regionCode, NATIONWIDE_LABEL);
        }

        List<Predicate> ors = new ArrayList<>();
        // 전국 정책은 어떤 지역 필터에서도 항상 포함
        ors.add(cb.equal(regionCode, NATIONWIDE_LABEL));

        for (String sigungu : f.sigunguCodes()) {
            // 정확 매칭 (단일 대표 코드)
            ors.add(cb.equal(regionCode, sigungu));
            // CSV 안 멤버십 (콤마 패딩으로 false-positive 방지)
            ors.add(cb.like(paddedCsv, "%," + sigungu + ",%"));
        }
        for (String sido : f.sidoCodes()) {
            // 정확 매칭 — 시·도 자체 코드 (예: regionCode = "11")
            ors.add(cb.equal(regionCode, sido));
            // 5자리 행정코드의 prefix (예: "11680" 은 "11" 로 시작)
            ors.add(cb.like(regionCode, sido + "___"));
            // CSV 안 시·도 코드 정확 매칭 — ",11," 형태
            ors.add(cb.like(paddedCsv, "%," + sido + ",%"));
            // CSV 안 시·도 prefix 5자리 — ",11xxx," 형태. SQL '_' 와이드카드로 정확히 5자리만.
            ors.add(cb.like(paddedCsv, "%," + sido + "___,%"));
        }
        return cb.or(ors.toArray(new Predicate[0]));
    }

    private static void applyOrder(Root<Policy> root, CriteriaQuery<?> query,
                                   CriteriaBuilder cb, PolicyStatus status) {
        if (query == null) return;
        Class<?> resultType = query.getResultType();
        if (resultType == Long.class || resultType == long.class) return;
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

    private static Expression<String> effectiveStatusExpr(Root<Policy> root, CriteriaBuilder cb) {
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        Path<LocalDate> applyStart = root.get("applyStart");
        Path<LocalDate> applyEnd = root.get("applyEnd");
        Path<Integer> referenceYear = root.get("referenceYear");

        return cb.<String>selectCase()
                .when(cb.and(cb.isNotNull(applyEnd), cb.lessThan(applyEnd, today)),
                        PolicyStatus.CLOSED.name())
                .when(cb.and(cb.isNotNull(applyStart), cb.greaterThan(applyStart, today)),
                        PolicyStatus.UPCOMING.name())
                .when(cb.and(cb.isNotNull(applyStart), cb.isNotNull(applyEnd)),
                        PolicyStatus.OPEN.name())
                .when(cb.and(cb.isNotNull(referenceYear), cb.lessThan(referenceYear, currentYear)),
                        PolicyStatus.CLOSED.name())
                .when(cb.and(cb.isNotNull(referenceYear), cb.equal(referenceYear, currentYear)),
                        PolicyStatus.OPEN.name())
                .otherwise(PolicyStatus.UPCOMING.name())
                .as(String.class);
    }
}
```

- [ ] **Step 5: 통과 확인**

```bash
./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicySpecificationTest"
```
Expected: PASS (기존 + 새 4개)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySpecificationTest.java
git commit -m "$(cat <<'EOF'
feat(policy): PolicySpecification 다중 지역 필터 + 콤마 패딩 LIKE

RegionFilter 를 받아 시·도/시·군·구 OR 합집합 + "전국" 정책 항상 포함.
CSV regionCodes 의 멤버십은 ',' || csv || ',' 패딩 후 LIKE '%,code,%'
패턴으로 false-positive 회피.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Repository 시그니처 전파

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java`

- [ ] **Step 1: 인터페이스 시그니처 변경**

`PolicyRepository.java` 의 `findAllByFilters` 시그니처를 변경:

```java
import com.youthfit.policy.domain.model.RegionFilter;
// ...
Page<Policy> findAllByFilters(RegionFilter regionFilter, Category category, PolicyStatus status,
                              Pageable pageable);
```

- [ ] **Step 2: 구현체 변경**

`PolicyRepositoryImpl.java`:

```java
@Override
public Page<Policy> findAllByFilters(RegionFilter regionFilter, Category category, PolicyStatus status,
                                     Pageable pageable) {
    return jpaRepository.findAll(
            PolicySpecification.withFilters(regionFilter, category, status), pageable);
}
```

import 추가: `import com.youthfit.policy.domain.model.RegionFilter;`

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: SUCCESS (Service 에서 호출하는 부분이 곧 깨질 수 있지만 Repository 자체는 컴파일됨)

- [ ] **Step 4: 커밋 (Task 4 와 묶어 진행하므로 여기서는 stage 만)**

이 Task 와 Task 4 는 시그니처 전파가 한 흐름이므로 Task 4 끝에서 함께 커밋한다. 여기서는 코드 변경만.

---

### Task 4: Service 시그니처 전파 + 테스트 업데이트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java`

- [ ] **Step 1: PolicyQueryService 시그니처 변경**

```java
// PolicyQueryService.java
import com.youthfit.policy.domain.model.RegionFilter;
// ...

public PolicyPageResult findPoliciesByFilters(RegionFilter regionFilter,
                                              Category category,
                                              PolicyStatus status,
                                              int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<Policy> policyPage = policyRepository.findAllByFilters(regionFilter, category, status, pageable);
    return toPageResult(policyPage);
}
```

- [ ] **Step 2: 테스트 시그니처 업데이트**

`PolicyQueryServiceTest.java` 에서 `findPoliciesByFilters` 호출부를 모두 새 시그니처로 변경.

기존:
```java
policyQueryService.findPoliciesByFilters(null, null, null, 0, 20)
```
→
```java
policyQueryService.findPoliciesByFilters(RegionFilter.of(null), null, null, 0, 20)
```

`given(policyRepository.findAllByFilters(...))` 의 인자도 새 시그니처에 맞게 (`any(RegionFilter.class), any(), any(), any()`) 로 갱신.

import 추가: `import com.youthfit.policy.domain.model.RegionFilter;`

- [ ] **Step 3: 통과 확인**

```bash
./gradlew test --tests "com.youthfit.policy.application.service.PolicyQueryServiceTest"
```
Expected: PASS

- [ ] **Step 4: 커밋 (Task 3 + 4 통합)**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java \
        backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java \
        backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java
git commit -m "$(cat <<'EOF'
refactor(policy): Repository/Service 시그니처를 RegionFilter 로 전파

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Controller — regions 파라미터 + legacy regionCode 호환

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java`
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyApi.java`
- Modify: `backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyControllerTest.java`

- [ ] **Step 1: PolicyApi 시그니처 + Swagger 갱신**

```java
@Operation(summary = "정책 목록 조회",
        description = "필터 조건에 따라 정책 목록을 페이징 조회한다. regions 는 행정코드 CSV (예: '11680,11440,11')."
                + " 시·도(2자리)/시·군·구(5자리) 혼합 가능. 'NATIONWIDE' 단독은 전국 정책만 반환."
                + " 그 외에는 '전국' 정책이 항상 OR 로 포함된다."
                + " regionCode(단수) 는 deprecated — regions 가 있으면 무시된다.")
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
})
@SecurityRequirements
ResponseEntity<PolicyPageResponse> findPolicies(
        @Parameter(description = "행정코드 CSV. 예: '11680,11440' 또는 'NATIONWIDE'") String regions,
        @Parameter(description = "[Deprecated] 단일 지역 코드. regions 가 있으면 무시.") String regionCode,
        Category category,
        @Parameter(description = "정책 상태 필터: OPEN(진행중) / UPCOMING(예정) / CLOSED(마감). 미지정 시 전체.")
        PolicyStatus status,
        int page,
        int size);
```

- [ ] **Step 2: PolicyController 구현 변경**

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

private static RegionFilter resolveRegionFilter(String regions, String legacyRegionCode) {
    if (regions != null && !regions.isBlank()) {
        return RegionFilter.ofCsv(regions);
    }
    if (legacyRegionCode != null && !legacyRegionCode.isBlank()) {
        return RegionFilter.of(java.util.List.of(legacyRegionCode));
    }
    return RegionFilter.of(null);
}
```

import 추가:
```java
import com.youthfit.policy.domain.model.RegionFilter;
```

- [ ] **Step 3: 테스트 업데이트 + 호환 케이스 추가**

`PolicyControllerTest.java` 의 기존 mocking 인자를 새 시그니처에 맞게 조정:

```java
given(policyQueryService.findPoliciesByFilters(any(RegionFilter.class), any(), any(), anyInt(), anyInt()))
        .willReturn(pageResult);
```

신규 테스트:

```java
@Test
@DisplayName("GET /api/v1/policies - regions CSV 파라미터를 전달하면 RegionFilter 로 변환된다")
void findPolicies_withRegions_passesRegionFilter() throws Exception {
    // given
    PolicyPageResult pageResult = new PolicyPageResult(java.util.List.of(), 0L, 0, 20, 0, false);
    given(policyQueryService.findPoliciesByFilters(any(RegionFilter.class), any(), any(), anyInt(), anyInt()))
            .willReturn(pageResult);

    // when & then
    mockMvc.perform(get("/api/v1/policies").param("regions", "11680,11440"))
            .andExpect(status().isOk());

    org.mockito.ArgumentCaptor<RegionFilter> captor =
            org.mockito.ArgumentCaptor.forClass(RegionFilter.class);
    then(policyQueryService).should().findPoliciesByFilters(
            captor.capture(), any(), any(), anyInt(), anyInt());
    RegionFilter passed = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(passed.sigunguCodes())
            .containsExactly("11680", "11440");
}

@Test
@DisplayName("GET /api/v1/policies - legacy regionCode 파라미터도 받지만 regions 가 우선한다")
void findPolicies_legacyRegionCode_compatible() throws Exception {
    PolicyPageResult pageResult = new PolicyPageResult(java.util.List.of(), 0L, 0, 20, 0, false);
    given(policyQueryService.findPoliciesByFilters(any(RegionFilter.class), any(), any(), anyInt(), anyInt()))
            .willReturn(pageResult);

    mockMvc.perform(get("/api/v1/policies").param("regionCode", "11680"))
            .andExpect(status().isOk());

    org.mockito.ArgumentCaptor<RegionFilter> captor =
            org.mockito.ArgumentCaptor.forClass(RegionFilter.class);
    then(policyQueryService).should().findPoliciesByFilters(
            captor.capture(), any(), any(), anyInt(), anyInt());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().sigunguCodes())
            .containsExactly("11680");
}

@Test
@DisplayName("GET /api/v1/policies - regions 와 regionCode 가 모두 오면 regions 우선")
void findPolicies_regionsWins() throws Exception {
    PolicyPageResult pageResult = new PolicyPageResult(java.util.List.of(), 0L, 0, 20, 0, false);
    given(policyQueryService.findPoliciesByFilters(any(RegionFilter.class), any(), any(), anyInt(), anyInt()))
            .willReturn(pageResult);

    mockMvc.perform(get("/api/v1/policies")
                    .param("regions", "11680")
                    .param("regionCode", "SEOUL"))
            .andExpect(status().isOk());

    org.mockito.ArgumentCaptor<RegionFilter> captor =
            org.mockito.ArgumentCaptor.forClass(RegionFilter.class);
    then(policyQueryService).should().findPoliciesByFilters(
            captor.capture(), any(), any(), anyInt(), anyInt());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().sigunguCodes())
            .containsExactly("11680");
}
```

import 추가:
```java
import com.youthfit.policy.domain.model.RegionFilter;
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew test --tests "com.youthfit.policy.presentation.controller.PolicyControllerTest"
```
Expected: PASS

- [ ] **Step 5: 전체 백엔드 테스트 회귀 확인**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyApi.java \
        backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyController.java \
        backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyControllerTest.java
git commit -m "$(cat <<'EOF'
feat(policy): /api/v1/policies 에 regions CSV 파라미터 + legacy regionCode 호환

regions: 행정코드 CSV. 시·도/시·군·구/NATIONWIDE 혼합 가능.
regionCode: deprecated, regions 가 없을 때만 단일 코드로 해석.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Region 마스터 API (RegionController + Service + DTO)

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/presentation/controller/RegionApi.java`
- Create: `backend/src/main/java/com/youthfit/policy/presentation/controller/RegionController.java`
- Create: `backend/src/main/java/com/youthfit/policy/application/service/RegionQueryService.java`
- Create: `backend/src/main/java/com/youthfit/policy/application/dto/result/RegionListResult.java`
- Create: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/RegionListResponse.java`
- Create: `backend/src/test/java/com/youthfit/policy/application/service/RegionQueryServiceTest.java`
- Create: `backend/src/test/java/com/youthfit/policy/presentation/controller/RegionControllerTest.java`

**현재 한계:** `RegionCodeRegistry.findAll(List<String>)` 은 null/empty 시 빈 리스트를 반환한다 (전체 조회 메서드 없음). 그래서 RegionQueryService 가 직접 `JsonRegionCodeRegistry` 의 내부 데이터에 접근하긴 어렵다. 두 옵션:

1. **Port 확장**: `RegionCodeRegistry` 에 `List<RegionCode> findAll()` (인자 없음) 메서드 추가.
2. **별도 서비스가 region-codes.json 을 다시 로드**.

선택: **옵션 1** — Port 확장이 표준적이고 단순.

- [ ] **Step 1: RegionCodeRegistry 에 findAll() 추가 (port + impl)**

`RegionCodeRegistry.java`:
```java
public interface RegionCodeRegistry {
    Optional<RegionCode> find(String code);
    List<RegionCode> findAll(List<String> codes);
    List<RegionCode> findAll();  // 신규
}
```

`JsonRegionCodeRegistry.java` 끝에 추가:
```java
@Override
public List<RegionCode> findAll() {
    return List.copyOf(map.values());
}
```

- [ ] **Step 2: 기존 JsonRegionCodeRegistryTest 에 findAll() 테스트 추가**

```java
@Test
@DisplayName("findAll() — 전체 행정코드를 반환한다 (서울 25개 구 포함)")
void findAll_returnsAll() {
    List<RegionCode> all = registry.findAll();
    assertThat(all).isNotEmpty();
    assertThat(all).extracting(RegionCode::sidoName).contains("서울특별시");
    long seoulGuCount = all.stream().filter(r -> "11".equals(r.sidoCode())).count();
    assertThat(seoulGuCount).isGreaterThanOrEqualTo(25);
}
```

- [ ] **Step 3: 실패 → 구현 → 통과**

```bash
./gradlew test --tests "com.youthfit.policy.infrastructure.external.JsonRegionCodeRegistryTest"
```
Expected: PASS (구현이 함께 들어가므로 한 번에 통과)

- [ ] **Step 4: RegionListResult 작성**

`backend/src/main/java/com/youthfit/policy/application/dto/result/RegionListResult.java`:
```java
package com.youthfit.policy.application.dto.result;

import java.util.List;

public record RegionListResult(
        List<Sido> sidos,
        List<Sigungu> sigungus
) {
    public record Sido(String code, String name) {}
    public record Sigungu(String code, String sidoCode, String sidoName, String name) {}
}
```

- [ ] **Step 5: RegionQueryService 작성 + 실패 테스트**

`RegionQueryServiceTest.java`:
```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.result.RegionListResult;
import com.youthfit.policy.application.port.RegionCodeRegistry;
import com.youthfit.policy.domain.model.RegionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("RegionQueryService")
@ExtendWith(MockitoExtension.class)
class RegionQueryServiceTest {

    @InjectMocks
    private RegionQueryService regionQueryService;

    @Mock
    private RegionCodeRegistry regionCodeRegistry;

    @Test
    @DisplayName("findAllRegions - 시·도 distinct + 시·군·구 전체 반환")
    void findAllRegions_returnsBothLists() {
        // given
        given(regionCodeRegistry.findAll()).willReturn(List.of(
                new RegionCode("11680", "11", "서울특별시", "강남구"),
                new RegionCode("11440", "11", "서울특별시", "마포구"),
                new RegionCode("26260", "26", "부산광역시", "동구")
        ));

        // when
        RegionListResult result = regionQueryService.findAllRegions();

        // then
        assertThat(result.sidos()).hasSize(2);
        assertThat(result.sidos()).extracting(RegionListResult.Sido::code)
                .containsExactly("11", "26");
        assertThat(result.sidos()).extracting(RegionListResult.Sido::name)
                .containsExactly("서울특별시", "부산광역시");
        assertThat(result.sigungus()).hasSize(3);
        assertThat(result.sigungus()).extracting(RegionListResult.Sigungu::code)
                .containsExactly("11680", "11440", "26260");
    }
}
```

`RegionQueryService.java`:
```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.result.RegionListResult;
import com.youthfit.policy.application.port.RegionCodeRegistry;
import com.youthfit.policy.domain.model.RegionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionQueryService {

    private final RegionCodeRegistry regionCodeRegistry;

    public RegionListResult findAllRegions() {
        List<RegionCode> all = regionCodeRegistry.findAll();
        Map<String, String> sidoOrder = new LinkedHashMap<>();
        List<RegionListResult.Sigungu> sigungus = new java.util.ArrayList<>();
        for (RegionCode rc : all) {
            sidoOrder.putIfAbsent(rc.sidoCode(), rc.sidoName());
            sigungus.add(new RegionListResult.Sigungu(rc.code(), rc.sidoCode(), rc.sidoName(), rc.name()));
        }
        List<RegionListResult.Sido> sidos = sidoOrder.entrySet().stream()
                .map(e -> new RegionListResult.Sido(e.getKey(), e.getValue()))
                .toList();
        return new RegionListResult(sidos, sigungus);
    }
}
```

- [ ] **Step 6: 통과 확인**

```bash
./gradlew test --tests "com.youthfit.policy.application.service.RegionQueryServiceTest"
```
Expected: PASS

- [ ] **Step 7: Response DTO + Api + Controller 작성**

`RegionListResponse.java`:
```java
package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.RegionListResult;

import java.util.List;

public record RegionListResponse(List<Sido> sidos, List<Sigungu> sigungus) {

    public record Sido(String code, String name) {}
    public record Sigungu(String code, String sidoCode, String sidoName, String name) {}

    public static RegionListResponse from(RegionListResult result) {
        return new RegionListResponse(
                result.sidos().stream().map(s -> new Sido(s.code(), s.name())).toList(),
                result.sigungus().stream()
                        .map(g -> new Sigungu(g.code(), g.sidoCode(), g.sidoName(), g.name())).toList()
        );
    }
}
```

`RegionApi.java`:
```java
package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.presentation.dto.response.RegionListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "지역", description = "행정코드 마스터 데이터 조회 API")
public interface RegionApi {

    @Operation(summary = "전체 지역 마스터 조회",
            description = "시·도(2자리) + 시·군·구(5자리) 행정코드 전체 목록을 반환한다. "
                    + "응답은 24시간 캐시 가능.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<RegionListResponse> findAllRegions();
}
```

`RegionController.java`:
```java
package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.RegionListResult;
import com.youthfit.policy.application.service.RegionQueryService;
import com.youthfit.policy.presentation.dto.response.RegionListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController implements RegionApi {

    private final RegionQueryService regionQueryService;

    @GetMapping
    @Override
    public ResponseEntity<RegionListResponse> findAllRegions() {
        RegionListResult result = regionQueryService.findAllRegions();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(RegionListResponse.from(result));
    }
}
```

- [ ] **Step 8: Controller 테스트**

`RegionControllerTest.java`:
```java
package com.youthfit.policy.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.policy.application.dto.result.RegionListResult;
import com.youthfit.policy.application.service.RegionQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("RegionController")
@WebMvcTest(controllers = RegionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class RegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionQueryService regionQueryService;

    @Test
    @DisplayName("GET /api/v1/regions - 시·도 + 시·군·구 목록을 반환한다")
    void findAllRegions_returns200WithBothLists() throws Exception {
        RegionListResult mockResult = new RegionListResult(
                List.of(new RegionListResult.Sido("11", "서울특별시")),
                List.of(new RegionListResult.Sigungu("11680", "11", "서울특별시", "강남구"))
        );
        given(regionQueryService.findAllRegions()).willReturn(mockResult);

        mockMvc.perform(get("/api/v1/regions"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andExpect(jsonPath("$.sidos[0].code").value("11"))
                .andExpect(jsonPath("$.sidos[0].name").value("서울특별시"))
                .andExpect(jsonPath("$.sigungus[0].code").value("11680"))
                .andExpect(jsonPath("$.sigungus[0].name").value("강남구"));
    }
}
```

- [ ] **Step 9: 통과 확인**

```bash
./gradlew test --tests "com.youthfit.policy.presentation.controller.RegionControllerTest"
./gradlew test
```
Expected: 둘 다 PASS

- [ ] **Step 10: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/port/RegionCodeRegistry.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/external/JsonRegionCodeRegistry.java \
        backend/src/main/java/com/youthfit/policy/application/dto/result/RegionListResult.java \
        backend/src/main/java/com/youthfit/policy/application/service/RegionQueryService.java \
        backend/src/main/java/com/youthfit/policy/presentation/dto/response/RegionListResponse.java \
        backend/src/main/java/com/youthfit/policy/presentation/controller/RegionApi.java \
        backend/src/main/java/com/youthfit/policy/presentation/controller/RegionController.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/external/JsonRegionCodeRegistryTest.java \
        backend/src/test/java/com/youthfit/policy/application/service/RegionQueryServiceTest.java \
        backend/src/test/java/com/youthfit/policy/presentation/controller/RegionControllerTest.java
git commit -m "$(cat <<'EOF'
feat(policy): GET /api/v1/regions 행정코드 마스터 API 신설

시·도(distinct) + 시·군·구 전체를 한 번에 반환. Cache-Control 24h.
RegionCodeRegistry.findAll() 추가로 전체 조회 지원.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2 — Frontend Data Layer

### Task 7: SIDO_CODE_BY_ENUM 매핑 + regionFilter 헬퍼

**Files:**
- Modify: `frontend/src/lib/labels/region.ts`
- Create: `frontend/src/lib/labels/region.test.ts`
- Create: `frontend/src/lib/regionFilter.ts`
- Create: `frontend/src/lib/regionFilter.test.ts`

- [ ] **Step 1: region.ts 에 enum → 행정코드 매핑 추가**

`frontend/src/lib/labels/region.ts` 끝에 추가:

```ts
/**
 * RegionSidoCode (enum) ↔ 행정구역 시·도 코드(2자리).
 * 행정안전부 행정코드 기준.
 */
export const SIDO_CODE_BY_ENUM: Record<RegionSidoCode, string> = {
  SEOUL: '11',
  BUSAN: '26',
  DAEGU: '27',
  INCHEON: '28',
  GWANGJU: '29',
  DAEJEON: '30',
  ULSAN: '31',
  SEJONG: '36',
  GYEONGGI: '41',
  GANGWON: '42',
  CHUNGBUK: '43',
  CHUNGNAM: '44',
  JEONBUK: '45',
  JEONNAM: '46',
  GYEONGBUK: '47',
  GYEONGNAM: '48',
  JEJU: '50',
};

export function sidoCodeOf(enumCode: RegionSidoCode): string {
  return SIDO_CODE_BY_ENUM[enumCode];
}
```

- [ ] **Step 2: region.test.ts 단위 테스트**

```ts
import { describe, it, expect } from 'vitest';
import {
  REGION_SIDO_OPTIONS,
  SIDO_CODE_BY_ENUM,
  sidoCodeOf,
} from './region';

describe('SIDO_CODE_BY_ENUM', () => {
  it('17개 광역시도 모두를 행정코드와 매핑한다', () => {
    expect(Object.keys(SIDO_CODE_BY_ENUM)).toHaveLength(17);
    REGION_SIDO_OPTIONS.forEach((code) => {
      expect(SIDO_CODE_BY_ENUM[code]).toMatch(/^\d{2}$/);
    });
  });

  it('주요 시·도 행정코드가 올바르다', () => {
    expect(SIDO_CODE_BY_ENUM.SEOUL).toBe('11');
    expect(SIDO_CODE_BY_ENUM.BUSAN).toBe('26');
    expect(SIDO_CODE_BY_ENUM.GYEONGGI).toBe('41');
    expect(SIDO_CODE_BY_ENUM.JEJU).toBe('50');
  });

  it('sidoCodeOf 는 동일한 매핑을 함수로 노출한다', () => {
    expect(sidoCodeOf('SEOUL')).toBe('11');
  });
});
```

- [ ] **Step 3: regionFilter.ts 헬퍼 작성**

```ts
/**
 * URL ?regions= CSV 와 string[] 간 변환 + 시·도/시·군·구 분류 헬퍼.
 *
 * NATIONWIDE 토큰은 "전국만 보기" 모드를 의미한다.
 */
export const NATIONWIDE_TOKEN = 'NATIONWIDE';

export interface RegionSelection {
  isNationwideOnly: boolean;
  sidoCodes: string[];     // 2자리
  sigunguCodes: string[];  // 5자리
}

export function parseRegionsParam(csv: string | null): string[] {
  if (!csv) return [];
  return csv
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

export function toRegionsParam(codes: string[]): string {
  return codes.join(',');
}

export function classifyRegionCodes(codes: string[]): RegionSelection {
  const seen = new Set<string>();
  let hasNationwide = false;
  const sidos: string[] = [];
  const sigungus: string[] = [];

  for (const raw of codes) {
    const code = raw.trim();
    if (!code || seen.has(code)) continue;
    seen.add(code);

    if (code === NATIONWIDE_TOKEN || code === '전국') {
      hasNationwide = true;
      continue;
    }
    if (!/^\d+$/.test(code)) continue;
    if (code.length === 2) sidos.push(code);
    else if (code.length === 5) sigungus.push(code);
  }

  return {
    isNationwideOnly: hasNationwide && sidos.length === 0 && sigungus.length === 0,
    sidoCodes: sidos,
    sigunguCodes: sigungus,
  };
}
```

- [ ] **Step 4: regionFilter.test.ts**

```ts
import { describe, it, expect } from 'vitest';
import {
  parseRegionsParam,
  toRegionsParam,
  classifyRegionCodes,
  NATIONWIDE_TOKEN,
} from './regionFilter';

describe('parseRegionsParam', () => {
  it('null/빈 문자열은 빈 배열', () => {
    expect(parseRegionsParam(null)).toEqual([]);
    expect(parseRegionsParam('')).toEqual([]);
  });

  it('공백을 trim 하고 빈 항목을 제외한다', () => {
    expect(parseRegionsParam(' 11 , ,11680 ')).toEqual(['11', '11680']);
  });
});

describe('toRegionsParam', () => {
  it('배열을 CSV 로 합친다', () => {
    expect(toRegionsParam(['11', '11680'])).toBe('11,11680');
  });
});

describe('classifyRegionCodes', () => {
  it('2자리는 시·도, 5자리는 시·군·구로 분류', () => {
    const r = classifyRegionCodes(['11', '11680', '26']);
    expect(r.sidoCodes).toEqual(['11', '26']);
    expect(r.sigunguCodes).toEqual(['11680']);
    expect(r.isNationwideOnly).toBe(false);
  });

  it('NATIONWIDE 단독은 전국만 모드', () => {
    const r = classifyRegionCodes([NATIONWIDE_TOKEN]);
    expect(r.isNationwideOnly).toBe(true);
  });

  it("'전국' 한글 별칭도 NATIONWIDE 로 인식", () => {
    expect(classifyRegionCodes(['전국']).isNationwideOnly).toBe(true);
  });

  it('NATIONWIDE 가 다른 코드와 함께 오면 일반 필터', () => {
    const r = classifyRegionCodes(['NATIONWIDE', '11']);
    expect(r.isNationwideOnly).toBe(false);
    expect(r.sidoCodes).toEqual(['11']);
  });

  it('중복 코드 제거 + 공백 trim', () => {
    const r = classifyRegionCodes(['11', '11', ' 11680 ', '11680']);
    expect(r.sidoCodes).toEqual(['11']);
    expect(r.sigunguCodes).toEqual(['11680']);
  });

  it('알 수 없는 길이/문자는 무시', () => {
    const r = classifyRegionCodes(['1', '123', 'ABC', '11']);
    expect(r.sidoCodes).toEqual(['11']);
    expect(r.sigunguCodes).toEqual([]);
  });
});
```

- [ ] **Step 5: 통과 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/frontend
npm run test -- src/lib/labels/region.test.ts src/lib/regionFilter.test.ts
```
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/lib/labels/region.ts \
        frontend/src/lib/labels/region.test.ts \
        frontend/src/lib/regionFilter.ts \
        frontend/src/lib/regionFilter.test.ts
git commit -m "$(cat <<'EOF'
feat(frontend): 지역 enum↔행정코드 매핑 + URL CSV 헬퍼

SIDO_CODE_BY_ENUM: RegionSidoCode → 2자리 행정코드.
regionFilter.ts: parseRegionsParam / toRegionsParam / classifyRegionCodes.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: region.api.ts + useRegions 훅

**Files:**
- Create: `frontend/src/types/region.ts`
- Create: `frontend/src/apis/region.api.ts`
- Create: `frontend/src/hooks/queries/useRegions.ts`

- [ ] **Step 1: 클라이언트 타입 정의**

`frontend/src/types/region.ts`:
```ts
export interface RegionSido {
  code: string;       // 2자리 행정코드
  name: string;       // 시·도명
}

export interface RegionSigungu {
  code: string;       // 5자리 행정코드
  sidoCode: string;
  sidoName: string;
  name: string;       // 시·군·구명
}

export interface RegionListResponse {
  sidos: RegionSido[];
  sigungus: RegionSigungu[];
}
```

- [ ] **Step 2: region.api.ts**

```ts
import api from './client';
import type { RegionListResponse } from '@/types/region';

export async function fetchRegions(): Promise<RegionListResponse> {
  return api.get('v1/regions').json<RegionListResponse>();
}
```

- [ ] **Step 3: useRegions 훅**

```ts
import { useQuery } from '@tanstack/react-query';
import { fetchRegions } from '@/apis/region.api';

export function useRegions() {
  return useQuery({
    queryKey: ['regions'],
    queryFn: fetchRegions,
    staleTime: Infinity,            // 24h 캐시 + 세션 중 재요청 안 함
    gcTime: 1000 * 60 * 60 * 24,    // 24h
  });
}
```

- [ ] **Step 4: 타입 체크**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/frontend
npx tsc --noEmit
```
Expected: 0 errors

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/types/region.ts \
        frontend/src/apis/region.api.ts \
        frontend/src/hooks/queries/useRegions.ts
git commit -m "$(cat <<'EOF'
feat(frontend): useRegions 훅 + GET /api/v1/regions 클라이언트

staleTime Infinity 로 세션 1회 조회. 마스터 데이터 변경 빈도 매우 낮음.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: policy.api / usePolicies 에 regions 파라미터 추가

**Files:**
- Modify: `frontend/src/apis/policy.api.ts`
- Modify: `frontend/src/hooks/queries/usePolicies.ts`

- [ ] **Step 1: PolicyListParams 확장**

`policy.api.ts`:
```ts
import api from './client';
import type { PolicyPage, PolicyDetail, PolicyStatus } from '@/types/policy';

interface PolicyListParams {
  category?: string;
  regions?: string[];         // 신규: 행정코드 배열 (서버에서 CSV 로 join)
  regionCode?: string;        // deprecated: regions 가 있으면 무시
  status?: PolicyStatus;
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

- [ ] **Step 2: usePolicies 훅 시그니처 변경**

```ts
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { fetchPolicies, searchPolicies } from '@/apis/policy.api';
import type { PolicyCategory, PolicyStatus } from '@/types/policy';

interface UsePoliciesParams {
  keyword?: string;
  category?: PolicyCategory | '';
  status?: PolicyStatus | '';
  regions?: string[];         // 신규
  page?: number;
  size?: number;
}

export function usePolicies(params: UsePoliciesParams) {
  const { keyword, category, status, regions, page = 0, size = 6 } = params;
  const regionsKey = regions && regions.length > 0 ? regions.join(',') : '';

  return useQuery({
    queryKey: ['policies', { keyword, category, status, regions: regionsKey, page, size }],
    queryFn: () =>
      keyword
        ? searchPolicies(keyword, { status: status || undefined, page, size })
        : fetchPolicies({
            category: category || undefined,
            status: status || undefined,
            regions: regions && regions.length > 0 ? regions : undefined,
            page,
            size,
          }),
    placeholderData: keepPreviousData,
  });
}
```

- [ ] **Step 3: 타입 체크**

```bash
npx tsc --noEmit
```
Expected: PolicyListPage.tsx 에서 기존 `regionCode` 전달 부분이 에러를 낼 수 있음. 그건 Task 14 에서 정리되므로 일시적으로 임시 처리:

`PolicyListPage.tsx` 의 `usePolicies({...})` 호출에서 `regionCode: regionCode || undefined,` 라인을 잠시 주석 처리. (Task 14 에서 picker 통합과 함께 정식 처리)

```bash
npx tsc --noEmit
```
Expected: 0 errors

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/apis/policy.api.ts \
        frontend/src/hooks/queries/usePolicies.ts \
        frontend/src/pages/PolicyListPage.tsx
git commit -m "$(cat <<'EOF'
feat(frontend): policy API 에 regions[] 파라미터 추가

regionCode 단수는 deprecated. usePolicies queryKey 도 regions 반영.
PolicyListPage 의 regionCode 전달은 Task 14에서 picker 통합과 함께 교체.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3 — Frontend UI

### Task 10: RegionPickerBanner 컴포넌트 (TDD)

**Files:**
- Create: `frontend/src/components/policy/RegionPickerBanner.tsx`
- Create: `frontend/src/components/policy/RegionPickerBanner.test.tsx`

**책임:** 로그인 사용자에게 "📍 내 지역(서울 강남구)으로 보고 있어요 [해제]" 안내. `role="status"`. 가장 단순하므로 TDD 부터.

- [ ] **Step 1: 실패 테스트 작성**

`RegionPickerBanner.test.tsx`:
```tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import RegionPickerBanner from './RegionPickerBanner';

describe('RegionPickerBanner', () => {
  it('지역 라벨을 본문에 노출한다', () => {
    render(<RegionPickerBanner regionLabel="서울 강남구" onDismiss={() => {}} />);
    expect(screen.getByText(/서울 강남구/)).toBeInTheDocument();
  });

  it('role="status" 를 가진다', () => {
    render(<RegionPickerBanner regionLabel="서울" onDismiss={() => {}} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('"해제" 버튼 클릭 시 onDismiss 가 호출된다', () => {
    const onDismiss = vi.fn();
    render(<RegionPickerBanner regionLabel="서울" onDismiss={onDismiss} />);
    fireEvent.click(screen.getByRole('button', { name: /해제/ }));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 2: 실패 확인**

```bash
npm run test -- src/components/policy/RegionPickerBanner.test.tsx
```
Expected: FAIL — 모듈 없음

- [ ] **Step 3: 구현**

`RegionPickerBanner.tsx`:
```tsx
import { MapPin } from 'lucide-react';

interface RegionPickerBannerProps {
  regionLabel: string;
  onDismiss: () => void;
}

export default function RegionPickerBanner({ regionLabel, onDismiss }: RegionPickerBannerProps) {
  return (
    <div
      role="status"
      className="mb-4 flex items-center gap-2 rounded-xl border border-brand-100 bg-gradient-to-r from-brand-50 to-brand-100/50 px-4 py-3 text-sm"
    >
      <MapPin className="h-4 w-4 shrink-0 text-brand-800" aria-hidden="true" />
      <span className="flex-1 text-brand-900">
        <strong className="font-semibold">내 지역({regionLabel})</strong>으로 보고 있어요
      </span>
      <button
        type="button"
        onClick={onDismiss}
        className="rounded-md px-2 py-1 text-xs font-semibold text-brand-800 transition-colors hover:bg-brand-100"
      >
        해제
      </button>
    </div>
  );
}
```

- [ ] **Step 4: 통과 확인**

```bash
npm run test -- src/components/policy/RegionPickerBanner.test.tsx
```
Expected: PASS (3개 테스트)

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/policy/RegionPickerBanner.tsx \
        frontend/src/components/policy/RegionPickerBanner.test.tsx
git commit -m "$(cat <<'EOF'
feat(frontend): RegionPickerBanner 컴포넌트

"내 지역(라벨)으로 보고 있어요 [해제]" 안내 배너. role=status.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: RegionPickerTrigger 컴포넌트

**Files:**
- Create: `frontend/src/components/policy/RegionPickerTrigger.tsx`

**책임:** 칩 형태의 트리거 버튼. `📍 전체 ▾` / `📍 서울 강남구 ▾` / `📍 서울 +2 ▾` / `📍 전국 ▾` 의 4가지 표시 케이스. 클릭 시 picker 를 연다.

- [ ] **Step 1: 구현**

`RegionPickerTrigger.tsx`:
```tsx
import { MapPin, ChevronDown } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { RegionListResponse } from '@/types/region';
import { classifyRegionCodes } from '@/lib/regionFilter';

interface RegionPickerTriggerProps {
  selectedCodes: string[];
  regionData: RegionListResponse | undefined;
  onOpen: () => void;
  disabled?: boolean;
}

function buildLabel(selectedCodes: string[], data: RegionListResponse | undefined): string {
  if (selectedCodes.length === 0) return '전체';
  const { isNationwideOnly, sidoCodes, sigunguCodes } = classifyRegionCodes(selectedCodes);
  if (isNationwideOnly) return '전국만';
  if (!data) return `${selectedCodes.length}개 선택`;

  const sidoNames = new Map(data.sidos.map((s) => [s.code, s.name.replace('특별시', '').replace('광역시', '').replace('특별자치도', '').replace('특별자치시', '').replace('도', '')]));
  const sigunguMap = new Map(data.sigungus.map((g) => [g.code, g]));

  // 시·군·구만 있는 경우: 첫 항목을 풀네임으로 + 나머지 개수
  if (sidoCodes.length === 0 && sigunguCodes.length > 0) {
    const first = sigunguMap.get(sigunguCodes[0]);
    if (!first) return `${sigunguCodes.length}개 선택`;
    const firstSido = sidoNames.get(first.sidoCode) ?? first.sidoName;
    const rest = sigunguCodes.length - 1;
    return rest > 0 ? `${firstSido} ${first.name} +${rest}` : `${firstSido} ${first.name}`;
  }
  // 시·도만 있는 경우
  if (sigunguCodes.length === 0 && sidoCodes.length > 0) {
    const firstSido = sidoNames.get(sidoCodes[0]) ?? sidoCodes[0];
    const rest = sidoCodes.length - 1;
    return rest > 0 ? `${firstSido} +${rest}` : firstSido;
  }
  // 혼합
  const total = sidoCodes.length + sigunguCodes.length;
  return `${total}개 선택`;
}

export default function RegionPickerTrigger({
  selectedCodes,
  regionData,
  onOpen,
  disabled = false,
}: RegionPickerTriggerProps) {
  const label = buildLabel(selectedCodes, regionData);
  const active = selectedCodes.length > 0;
  const count = selectedCodes.length;

  return (
    <button
      type="button"
      onClick={onOpen}
      disabled={disabled}
      aria-label={count > 0 ? `지역 선택: ${label}` : '지역 선택'}
      aria-haspopup="dialog"
      className={cn(
        'inline-flex min-h-11 items-center gap-1 rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
        active
          ? 'border-transparent bg-brand-100 text-indigo-600'
          : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
        disabled && 'cursor-not-allowed opacity-50',
      )}
    >
      <MapPin className="h-4 w-4" aria-hidden="true" />
      <span className="max-w-[140px] truncate">{label}</span>
      <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
    </button>
  );
}
```

- [ ] **Step 2: 타입 체크**

```bash
npx tsc --noEmit
```
Expected: 0 errors

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/components/policy/RegionPickerTrigger.tsx
git commit -m "$(cat <<'EOF'
feat(frontend): RegionPickerTrigger 컴포넌트

칩 트리거. 선택 개수에 따라 '전체' / '서울' / '서울 강남구' /
'서울 강남구 +2' / '전국만' / 'N개 선택' 으로 라벨 분기.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: RegionPicker 본체 컴포넌트

**Files:**
- Create: `frontend/src/components/policy/RegionPicker.tsx`

**책임:** 모바일은 풀스크린 시트, 데스크톱은 460px 팝오버. 좌측 시·도 리스트, 우측 시·군·구 체크박스. 다중 선택. 적용/해제 CTA.

- [ ] **Step 1: 구현**

`RegionPicker.tsx`:
```tsx
import { useEffect, useMemo, useState } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { RegionListResponse, RegionSigungu } from '@/types/region';
import { NATIONWIDE_TOKEN, classifyRegionCodes } from '@/lib/regionFilter';

interface RegionPickerProps {
  open: boolean;
  onClose: () => void;
  selectedCodes: string[];
  onApply: (codes: string[]) => void;
  regionData: RegionListResponse | undefined;
  mode: 'mobile-sheet' | 'desktop-popover';
}

const NATIONWIDE_SIDO = { code: NATIONWIDE_TOKEN, name: '전국' } as const;

export default function RegionPicker({
  open,
  onClose,
  selectedCodes,
  onApply,
  regionData,
  mode,
}: RegionPickerProps) {
  const [draft, setDraft] = useState<string[]>(selectedCodes);
  const [activeSido, setActiveSido] = useState<string>('11'); // 기본 서울

  // open 될 때 selectedCodes 로 초기화
  useEffect(() => {
    if (open) setDraft(selectedCodes);
  }, [open, selectedCodes]);

  // 모바일 시트일 때 body 스크롤 잠금
  useEffect(() => {
    if (mode !== 'mobile-sheet') return;
    if (open) document.body.style.overflow = 'hidden';
    else document.body.style.overflow = '';
    return () => { document.body.style.overflow = ''; };
  }, [open, mode]);

  // ESC 닫기
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const sidos = useMemo(
    () => regionData?.sidos ?? [],
    [regionData],
  );
  const sigunguBySido = useMemo(() => {
    const map = new Map<string, RegionSigungu[]>();
    (regionData?.sigungus ?? []).forEach((g) => {
      const arr = map.get(g.sidoCode) ?? [];
      arr.push(g);
      map.set(g.sidoCode, arr);
    });
    return map;
  }, [regionData]);

  const draftClassified = useMemo(() => classifyRegionCodes(draft), [draft]);

  const countForSido = (sidoCode: string): number => {
    return draft.filter((c) => c.length === 5 && c.startsWith(sidoCode)).length;
  };

  const toggleSigungu = (code: string) => {
    setDraft((prev) => prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]);
  };

  const toggleSidoFull = (sidoCode: string) => {
    // 시·도 전체 토글: 그 시·도 코드 자체를 draft 에 추가/제거.
    // 추가 시 해당 시·도 산하 시·군·구 코드는 정리 (중복 의미 회피)
    setDraft((prev) => {
      if (prev.includes(sidoCode)) {
        return prev.filter((c) => c !== sidoCode);
      }
      const withoutSidoChildren = prev.filter((c) => !(c.length === 5 && c.startsWith(sidoCode)));
      return [...withoutSidoChildren, sidoCode];
    });
  };

  const toggleNationwide = () => {
    setDraft((prev) => prev.includes(NATIONWIDE_TOKEN) ? [] : [NATIONWIDE_TOKEN]);
  };

  const applyAndClose = () => {
    onApply(draft);
    onClose();
  };

  const resetAll = () => setDraft([]);

  if (!open) return null;

  const appliedCount = draft.length;
  const isNationwide = draftClassified.isNationwideOnly;

  const body = (
    <div className={cn(
      'flex flex-col bg-white',
      mode === 'mobile-sheet'
        ? 'fixed inset-0 z-50 md:hidden'
        : 'absolute right-0 top-full z-40 mt-2 w-[460px] overflow-hidden rounded-2xl border border-gray-200 shadow-xl',
    )}>
      <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3">
        <h2 id="region-picker-title" className="text-base font-bold text-gray-900">지역 선택</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-gray-100"
        >
          <X className="h-5 w-5 text-gray-500" />
        </button>
      </div>

      <div className="border-b border-gray-100 bg-green-50 px-4 py-2 text-xs text-green-700">
        ✓ "전국" 정책은 어떤 지역을 골라도 항상 함께 보여요
      </div>

      <div className={cn('flex flex-1 min-h-0', mode === 'desktop-popover' && 'h-[360px]')}>
        {/* 좌측 시·도 리스트 */}
        <div
          role="listbox"
          aria-label="시·도 선택"
          className="w-[38%] overflow-y-auto border-r border-gray-100 bg-gray-50"
        >
          <button
            type="button"
            role="option"
            aria-selected={isNationwide}
            onClick={() => {
              toggleNationwide();
              setActiveSido(NATIONWIDE_TOKEN);
            }}
            className={cn(
              'flex w-full items-center justify-between px-3 py-3 text-left text-sm border-b border-gray-100',
              isNationwide ? 'bg-white font-semibold text-brand-800' : 'text-gray-700 hover:bg-white',
            )}
          >
            <span>{NATIONWIDE_SIDO.name}</span>
          </button>
          {sidos.map((sido) => {
            const active = activeSido === sido.code;
            const cnt = countForSido(sido.code);
            const sidoSelected = draft.includes(sido.code);
            return (
              <button
                key={sido.code}
                type="button"
                role="option"
                aria-selected={active}
                onClick={() => setActiveSido(sido.code)}
                className={cn(
                  'flex w-full items-center justify-between px-3 py-3 text-left text-sm border-b border-gray-100',
                  active ? 'bg-white font-semibold text-brand-800' : 'text-gray-700 hover:bg-white',
                )}
              >
                <span>{sido.name.replace('특별시', '').replace('광역시', '').replace('특별자치도', '').replace('특별자치시', '').replace('도', '도')}</span>
                {(cnt > 0 || sidoSelected) && (
                  <span className="text-xs text-brand-800">
                    {sidoSelected ? '전체' : `${cnt}`}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* 우측 시·군·구 */}
        <div className="flex-1 overflow-y-auto">
          {isNationwide ? (
            <div className="p-6 text-center text-sm text-gray-500">
              전국만 보기 모드입니다. 시·군·구를 추가하려면 좌측에서 다른 시·도를 선택하세요.
            </div>
          ) : (
            <>
              {/* 시·도 전체 토글 */}
              {activeSido !== NATIONWIDE_TOKEN && (
                <label className="flex cursor-pointer items-center justify-between border-b border-gray-100 px-4 py-3 text-sm font-semibold text-gray-900 hover:bg-gray-50">
                  <span>
                    {sidos.find((s) => s.code === activeSido)?.name} 전체
                  </span>
                  <input
                    type="checkbox"
                    checked={draft.includes(activeSido)}
                    onChange={() => toggleSidoFull(activeSido)}
                    className="h-4 w-4 rounded border-gray-300 text-brand-800 focus:ring-brand-800"
                  />
                </label>
              )}
              {/* 시·군·구 리스트 */}
              {(sigunguBySido.get(activeSido) ?? []).map((g) => {
                const checked = draft.includes(g.code);
                return (
                  <label
                    key={g.code}
                    className="flex cursor-pointer items-center justify-between border-b border-gray-100 px-4 py-3 text-sm text-gray-700 hover:bg-gray-50"
                  >
                    <span>{g.name}</span>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleSigungu(g.code)}
                      className="h-4 w-4 rounded border-gray-300 text-brand-800 focus:ring-brand-800"
                    />
                  </label>
                );
              })}
            </>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="border-t border-gray-100 bg-gray-50 px-4 py-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={resetAll}
            className="rounded-md px-3 py-2 text-xs font-semibold text-gray-600 hover:bg-gray-100"
          >
            전체 해제
          </button>
          <button
            type="button"
            onClick={applyAndClose}
            className="flex-1 rounded-xl bg-brand-800 px-4 py-3 text-sm font-semibold text-white hover:bg-brand-900"
          >
            {appliedCount === 0 ? '전체 보기' : `${appliedCount}개 지역 적용`}
          </button>
        </div>
      </div>
    </div>
  );

  if (mode === 'mobile-sheet') {
    return (
      <div role="dialog" aria-modal="true" aria-labelledby="region-picker-title">
        <div className="fixed inset-0 z-40 bg-black/40 md:hidden" onClick={onClose} aria-hidden="true" />
        {body}
      </div>
    );
  }
  return (
    <div role="dialog" aria-modal="false" aria-labelledby="region-picker-title">
      {body}
    </div>
  );
}
```

- [ ] **Step 2: 타입 체크**

```bash
npx tsc --noEmit
```
Expected: 0 errors

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/components/policy/RegionPicker.tsx
git commit -m "$(cat <<'EOF'
feat(frontend): RegionPicker 2-컬럼 본체 컴포넌트

모바일 풀스크린 시트 / 데스크톱 460px 팝오버. 좌측 시·도, 우측 시·군·구
다중 체크박스. ESC 닫기, body 스크롤 잠금. role=dialog + listbox.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 13: 임시 미리보기 라우트 `/_dev/region-picker`

**Files:**
- Create: `frontend/src/pages/_dev/RegionPickerPlayground.tsx`
- Modify: `frontend/src/App.tsx`

**책임:** PolicyListPage 통합 전에 picker 를 단독으로 띄워보고 디자인을 검증. Task 16 에서 삭제.

- [ ] **Step 1: 페이지 작성**

`frontend/src/pages/_dev/RegionPickerPlayground.tsx`:
```tsx
import { useState } from 'react';
import RegionPicker from '@/components/policy/RegionPicker';
import RegionPickerTrigger from '@/components/policy/RegionPickerTrigger';
import RegionPickerBanner from '@/components/policy/RegionPickerBanner';
import { useRegions } from '@/hooks/queries/useRegions';

export default function RegionPickerPlayground() {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<string[]>(['11680', '11440']);
  const [bannerOn, setBannerOn] = useState(true);
  const [mode, setMode] = useState<'mobile-sheet' | 'desktop-popover'>('mobile-sheet');
  const { data, isLoading } = useRegions();

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="mb-6 text-2xl font-bold">RegionPicker Playground</h1>

      <div className="mb-4 flex gap-2 text-sm">
        <button
          className={mode === 'mobile-sheet' ? 'rounded bg-brand-800 px-3 py-1.5 text-white' : 'rounded border px-3 py-1.5'}
          onClick={() => setMode('mobile-sheet')}
        >mobile-sheet</button>
        <button
          className={mode === 'desktop-popover' ? 'rounded bg-brand-800 px-3 py-1.5 text-white' : 'rounded border px-3 py-1.5'}
          onClick={() => setMode('desktop-popover')}
        >desktop-popover</button>
      </div>

      {bannerOn && (
        <RegionPickerBanner regionLabel="서울 강남구" onDismiss={() => setBannerOn(false)} />
      )}

      <div className="relative inline-block">
        <RegionPickerTrigger
          selectedCodes={selected}
          regionData={data}
          onOpen={() => setOpen(true)}
        />
        <RegionPicker
          open={open}
          onClose={() => setOpen(false)}
          selectedCodes={selected}
          onApply={setSelected}
          regionData={data}
          mode={mode}
        />
      </div>

      <div className="mt-8 rounded-lg bg-gray-100 p-4">
        <p className="text-xs text-gray-500 mb-1">current selectedCodes</p>
        <code className="text-sm">{JSON.stringify(selected)}</code>
      </div>

      {isLoading && <p className="mt-4 text-sm text-gray-500">로딩 중…</p>}
    </div>
  );
}
```

- [ ] **Step 2: App.tsx 에 라우트 추가**

`AppLayout` 블록 안 `/mypage` 다음에 추가:
```tsx
import RegionPickerPlayground from '@/pages/_dev/RegionPickerPlayground';
// ...
<Route path="/_dev/region-picker" element={<RegionPickerPlayground />} />
```

- [ ] **Step 3: 개발 서버 띄우고 수동 검증**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
docker compose up -d        # 백엔드 + DB 가 떠 있어야 /api/v1/regions 동작
cd frontend
npm run dev
# 브라우저에서 http://localhost:5173/_dev/region-picker 접속
```

검증 항목:
- [ ] mobile-sheet 모드에서 시·도 클릭 → 우측 시·군·구가 보이고 체크 가능.
- [ ] desktop-popover 모드에서 트리거 클릭 시 우측 460px 카드.
- [ ] 좌측 "전국" 클릭 시 우측이 "전국만 보기" 안내로 바뀜.
- [ ] "시·도 전체" 체크 시 그 시·도 산하 시·군·구 체크가 정리됨.
- [ ] 적용 클릭 시 selectedCodes 가 갱신되고 picker 닫힘.
- [ ] 트리거 칩 라벨이 선택 상태에 따라 "전체" / "서울 강남구 +1" / "전국만" 으로 바뀜.
- [ ] 배너 "해제" 클릭 시 배너 사라짐.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/_dev/RegionPickerPlayground.tsx \
        frontend/src/App.tsx
git commit -m "$(cat <<'EOF'
chore(frontend): /_dev/region-picker 임시 미리보기 라우트

PolicyListPage 통합 전에 단독으로 picker 동작 검증. Task 16 에서 삭제.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 14: PolicyListPage 통합

**Files:**
- Modify: `frontend/src/pages/PolicyListPage.tsx`

**책임:** 지역 `<select>` 제거, `RegionPickerTrigger` + `RegionPicker` + `RegionPickerBanner` 배치, URL `?regions=` 파싱, `?regionCode=` legacy 정규화, 자동 적용 effect 구성.

- [ ] **Step 1: import 변경**

상단 import 영역에서:
```tsx
import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, SlidersHorizontal, X, ChevronLeft, ChevronRight, AlertCircle } from 'lucide-react';
import { cn } from '@/lib/cn';
import PolicyCard from '@/components/policy/PolicyCard';
import LoginPromptModal from '@/components/auth/LoginPromptModal';
import RegionPicker from '@/components/policy/RegionPicker';
import RegionPickerTrigger from '@/components/policy/RegionPickerTrigger';
import RegionPickerBanner from '@/components/policy/RegionPickerBanner';
import { usePolicies } from '@/hooks/queries/usePolicies';
import { useMyBookmarkIds } from '@/hooks/queries/useMyBookmarkIds';
import { useRegions } from '@/hooks/queries/useRegions';
import { useNotificationSettings } from '@/hooks/queries/useNotificationSettings';
import { useAddBookmark, useRemoveBookmark } from '@/hooks/mutations/useToggleBookmark';
import { useAuthStore } from '@/stores/authStore';
import type { PolicyCategory, PolicyStatus } from '@/types/policy';
import { CATEGORY_LABELS } from '@/types/policy';
import { SIDO_CODE_BY_ENUM } from '@/lib/labels/region';
import { parseRegionsParam, toRegionsParam } from '@/lib/regionFilter';
```

**삭제:** `REGION_OPTIONS` import (더 이상 사용 안 함).

- [ ] **Step 2: `MobileFilterSheet` 의 지역 select 제거**

`MobileFilterSheet` 컴포넌트의 props 에서 `regionCode` / `onRegionChange` 제거. `<fieldset className="mt-5">...지역 select...</fieldset>` 블록 통째로 삭제. 카테고리만 남긴다.

- [ ] **Step 3: 본문 컴포넌트 로직 교체**

`PolicyListPage` 함수 내부:

```tsx
export default function PolicyListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [filterSheetOpen, setFilterSheetOpen] = useState(false);
  const [regionPickerOpen, setRegionPickerOpen] = useState(false);
  const [bannerDismissed, setBannerDismissed] = useState(false);
  const [inputValue, setInputValue] = useState(searchParams.get('keyword') ?? '');
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const { data: bookmarkIdPairs } = useMyBookmarkIds();
  const { data: regionData } = useRegions();
  const { data: notificationSettings } = useNotificationSettings();
  const addBookmarkMutation = useAddBookmark();
  const removeBookmarkMutation = useRemoveBookmark();
  const autoAppliedRef = useRef(false);

  const bookmarkMap = useMemo(() => {
    const map = new Map<number, number>();
    for (const pair of bookmarkIdPairs ?? []) map.set(pair.policyId, pair.bookmarkId);
    return map;
  }, [bookmarkIdPairs]);

  const handleBookmarkToggle = useCallback(
    (policyId: number) => {
      if (!isAuthenticated) { setLoginModalOpen(true); return; }
      const bookmarkId = bookmarkMap.get(policyId);
      if (bookmarkId != null) removeBookmarkMutation.mutate(bookmarkId);
      else addBookmarkMutation.mutate(policyId);
    },
    [isAuthenticated, bookmarkMap, addBookmarkMutation, removeBookmarkMutation],
  );

  const keyword = searchParams.get('keyword') ?? '';
  const category = (searchParams.get('category') ?? '') as PolicyCategory | '';
  const rawStatus = searchParams.get('status');
  const status: PolicyStatus = isPolicyStatus(rawStatus) ? rawStatus : DEFAULT_STATUS;
  const regions = parseRegionsParam(searchParams.get('regions'));
  const page = Math.max(0, parseInt(searchParams.get('page') ?? '0', 10) || 0);

  // Legacy ?regionCode=SEOUL → ?regions=11 정규화 (1회)
  useEffect(() => {
    const legacy = searchParams.get('regionCode');
    if (!legacy) return;
    const mapped = SIDO_CODE_BY_ENUM[legacy as keyof typeof SIDO_CODE_BY_ENUM];
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('regionCode');
      if (mapped) next.set('regions', mapped);
      return next;
    }, { replace: true });
  }, [searchParams, setSearchParams]);

  // 자동 적용 effect — 로그인 + interestRegions 존재 + URL에 regions 없음일 때 1회 적용
  useEffect(() => {
    if (autoAppliedRef.current) return;
    if (!isAuthenticated) return;
    if (searchParams.has('regions')) return;
    if (!notificationSettings) return;
    const interest = notificationSettings.interestRegions;
    if (!interest || interest.length === 0) return;

    const sidoCode = SIDO_CODE_BY_ENUM[interest[0]];
    if (!sidoCode) return;

    autoAppliedRef.current = true;
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('regions', sidoCode);
      next.delete('page');
      return next;
    }, { replace: true });
  }, [isAuthenticated, notificationSettings, searchParams, setSearchParams]);

  // 자동 적용된 라벨 (배너용)
  const autoBannerLabel = useMemo(() => {
    if (!isAuthenticated || bannerDismissed) return null;
    if (!autoAppliedRef.current) return null;
    if (regions.length !== 1 || regions[0].length !== 2) return null;
    const sido = regionData?.sidos.find((s) => s.code === regions[0]);
    return sido ? sido.name : null;
  }, [isAuthenticated, bannerDismissed, regions, regionData]);

  const { data, isLoading, isError, refetch } = usePolicies({
    keyword: keyword || undefined,
    category,
    status,
    regions: regions.length > 0 ? regions : undefined,
    page,
    size: PAGE_SIZE,
  });

  const updateParams = useCallback(
    (updates: Record<string, string>) => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        for (const [k, v] of Object.entries(updates)) {
          if (v) next.set(k, v);
          else next.delete(k);
        }
        return next;
      });
    },
    [setSearchParams],
  );

  const handleStatusTabChange = useCallback(
    (next: PolicyStatus) => {
      updateParams({ status: next === DEFAULT_STATUS ? '' : next, page: '' });
    },
    [updateParams],
  );

  const handleRegionApply = useCallback((codes: string[]) => {
    updateParams({ regions: codes.length > 0 ? toRegionsParam(codes) : '', page: '' });
  }, [updateParams]);

  const handleBannerDismiss = useCallback(() => {
    setBannerDismissed(true);
    updateParams({ regions: '' });
  }, [updateParams]);

  const resetFilters = useCallback(() => {
    setSearchParams(new URLSearchParams());
    setInputValue('');
  }, [setSearchParams]);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (inputValue !== keyword) updateParams({ keyword: inputValue, page: '' });
    }, 300);
    return () => clearTimeout(timer);
  }, [inputValue, keyword, updateParams]);

  useEffect(() => {
    setInputValue(searchParams.get('keyword') ?? '');
  }, [searchParams]);

  const activeFilters: { key: string; label: string }[] = [];
  if (category) activeFilters.push({ key: 'category', label: CATEGORY_LABELS[category] });
  // 지역은 트리거 자체가 활성 칩 역할이므로 activeFilters 에서 제외

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateParams({ keyword: inputValue, page: '' });
  };

  const isSearchMode = Boolean(keyword);
  const hasActiveQuery = Boolean(keyword || category || regions.length > 0 || status !== DEFAULT_STATUS);

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6 md:py-12">
      {/* ── Header ── */}
      <header className="mb-8 text-center">
        <h1 className="text-2xl font-bold text-gray-900 md:text-3xl">청년 정책 찾기</h1>
        <p className="mt-2 text-sm text-gray-500 md:text-base">나에게 맞는 청년 정책을 찾아보세요.</p>
      </header>

      {/* ── Search bar ── */}
      <form
        role="search"
        aria-label="정책 검색"
        onSubmit={handleSearchSubmit}
        className="mx-auto mb-6 flex max-w-[680px] items-center gap-2 rounded-[20px] border border-neutral-200 bg-white px-4 shadow-card transition-shadow focus-within:shadow-lg"
      >
        <Search className="h-5 w-5 shrink-0 text-neutral-500" aria-hidden="true" />
        <input
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder="키워드로 정책을 검색하세요"
          className="h-14 flex-1 bg-transparent text-sm text-gray-900 placeholder:text-neutral-400 focus:outline-none"
          aria-label="정책 검색어"
        />
        <button type="submit" className="shrink-0 rounded-full bg-brand-800 px-5 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-900">검색</button>
      </form>

      {/* 자동 적용 배너 */}
      {autoBannerLabel && (
        <RegionPickerBanner regionLabel={autoBannerLabel} onDismiss={handleBannerDismiss} />
      )}

      <StatusTabBar status={status} onStatusChange={handleStatusTabChange} />

      {/* ── Desktop Filters ── */}
      <div className="mb-4 hidden flex-wrap items-center gap-2 md:flex">
        {CATEGORY_ENTRIES.map(([key, label]) => (
          <button
            key={key}
            onClick={() => updateParams({ category: category === key ? '' : key, page: '' })}
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
            disabled={isSearchMode}
          />
          {/* 데스크톱 팝오버 모드 */}
          <div className="hidden md:block">
            <RegionPicker
              open={regionPickerOpen}
              onClose={() => setRegionPickerOpen(false)}
              selectedCodes={regions}
              onApply={handleRegionApply}
              regionData={regionData}
              mode="desktop-popover"
            />
          </div>
        </div>

        {isSearchMode && (
          <span className="ml-2 text-xs text-gray-500">검색 결과에는 지역 필터가 적용되지 않습니다</span>
        )}
      </div>

      {/* ── Mobile Filter Bar ── */}
      <div className="mb-4 flex flex-wrap items-center gap-2 md:hidden">
        <button
          onClick={() => setFilterSheetOpen(true)}
          className="flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white px-4 py-2 text-sm font-semibold text-neutral-700 transition-colors hover:bg-gray-50"
          aria-label="필터 열기"
        >
          <SlidersHorizontal className="h-4 w-4" />
          필터
          {activeFilters.length > 0 && (
            <span className="ml-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand-800 text-xs text-white">
              {activeFilters.length}
            </span>
          )}
        </button>
        <RegionPickerTrigger
          selectedCodes={regions}
          regionData={regionData}
          onOpen={() => setRegionPickerOpen(true)}
          disabled={isSearchMode}
        />
        {isSearchMode && (
          <span className="text-[10px] text-gray-500">검색 결과 미적용</span>
        )}
      </div>

      <MobileFilterSheet
        isOpen={filterSheetOpen}
        onClose={() => setFilterSheetOpen(false)}
        category={category}
        onCategoryChange={(v) => updateParams({ category: v, page: '' })}
      />

      {/* 모바일 RegionPicker (sheet) */}
      <div className="md:hidden">
        <RegionPicker
          open={regionPickerOpen}
          onClose={() => setRegionPickerOpen(false)}
          selectedCodes={regions}
          onApply={handleRegionApply}
          regionData={regionData}
          mode="mobile-sheet"
        />
      </div>

      {/* ── Active filter badges ── */}
      {activeFilters.length > 0 && (
        <div className="mb-4 flex flex-wrap gap-2">
          {activeFilters.map((f) => (
            <span key={f.key} className="inline-flex items-center gap-1 rounded-full bg-brand-100 px-3 py-1 text-sm font-semibold text-indigo-600">
              {f.label}
              <button
                onClick={() => updateParams({ [f.key]: '', page: '' })}
                className="ml-0.5 flex h-4 w-4 items-center justify-center rounded-full hover:bg-brand-200"
                aria-label={`${f.label} 필터 제거`}
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
      )}

      {/* ── Result meta ── */}
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-gray-500">
          {data ? (
            <><span className="font-semibold text-gray-900">{data.totalCount ?? 0}개</span> 정책</>
          ) : (<span>&nbsp;</span>)}
        </p>
      </div>

      {/* ── 이하 기존 로딩/에러/빈 상태/리스트/페이지네이션 그대로 유지 ── */}
      {/* (Skeleton, error, empty state, grid, Pagination 컴포넌트 호출은 변경 없음) */}
      {/* ... */}

      <LoginPromptModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        message="로그인하면 정책을 북마크할 수 있어요"
      />
    </div>
  );
}
```

**중요:** 기존 PolicyListPage 의 로딩/에러/빈 상태/카드 그리드/페이지네이션 블록(`{isLoading && ...}` 부터 `</>` 직전까지) 은 그대로 보존한다. 위 코드는 그 블록 직전까지의 변경분만 보여준다.

`MobileFilterSheet` props 인터페이스도 함께 좁힌다:
```tsx
function MobileFilterSheet({
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
  // ... 기존 내용에서 지역 fieldset 만 삭제
}
```

- [ ] **Step 4: 타입 체크 + 빌드**

```bash
npx tsc --noEmit
npm run build
```
Expected: 0 errors

- [ ] **Step 5: 수동 검증 (스펙 §8.2 체크리스트 일부)**

```bash
npm run dev
# 백엔드도 떠 있어야 함: cd backend && ./gradlew bootRun (다른 터미널)
```

브라우저에서 http://localhost:5173/policies:
- [ ] 비로그인 첫 진입: 트리거 `📍 전체 ▾`, 배너 없음.
- [ ] 트리거 클릭 → picker 열림 → 서울 선택 → 강남구 체크 → 적용 → URL `?regions=11680`, 카드 목록 갱신.
- [ ] 다시 적용해 `?regions=11680,11440` 로 다중 선택 시 카드 그리드 갱신.
- [ ] 좌측 "전국" → 적용 → URL `?regions=NATIONWIDE`.
- [ ] (선택) 로그인 + interestRegions 존재 시 첫 진입에서 배너 + 자동 적용.
- [ ] 검색바에 키워드 입력 → 트리거 disabled + "검색 결과 미적용" 텍스트.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/pages/PolicyListPage.tsx
git commit -m "$(cat <<'EOF'
feat(frontend): PolicyListPage 에 RegionPicker 통합

지역 select 제거 → RegionPickerTrigger + RegionPicker.
URL ?regions= CSV 파싱, legacy ?regionCode= 정규화.
로그인 사용자 interestRegions 자동 적용 + 배너 해제 가능.
검색 모드에서는 picker disabled.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 15: PolicyCard 지역 뱃지

**Files:**
- Modify: `frontend/src/components/policy/PolicyCard.tsx`

**책임:** 카드 상단 뱃지 줄에 "전국" / "서울 강남구" 뱃지를 prepend. 회색(전국) vs 노란(지역) 톤으로 구분.

- [ ] **Step 1: 뱃지 컴포넌트 추가 + 배치**

`PolicyCard.tsx` 의 `StatusBadge` 다음에 `RegionBadge` 추가:

```tsx
function RegionBadge({ policy }: { policy: Policy }) {
  const isNationwide = policy.regionCode === '전국';

  if (isNationwide) {
    return (
      <span className="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-semibold text-gray-600">
        {getRegionName(policy.regionCode, policy.sourceType)}
      </span>
    );
  }

  // 시·군·구 정책: subRegions 첫 번째를 풀라벨로
  const first = policy.subRegions?.[0];
  if (!first) {
    // fallback — regionCode 자체 라벨
    return (
      <span className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-semibold text-amber-700">
        {getRegionName(policy.regionCode, policy.sourceType)}
      </span>
    );
  }
  const rest = (policy.subRegions?.length ?? 0) - 1;
  const label = rest > 0 ? `${first} +${rest}` : first;
  return (
    <span className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-semibold text-amber-700">
      {label}
    </span>
  );
}

export { CategoryBadge, StatusBadge, RegionBadge };
```

그리고 JSX 의 상단 뱃지 줄에 `RegionBadge` 를 가장 먼저 배치:

```tsx
{/* 상단: 배지 + 북마크 */}
<div className="mb-3 flex items-center gap-2">
  <RegionBadge policy={policy} />
  <CategoryBadge category={policy.category} />
  <StatusBadge status={effectiveStatus} />
  <SourceBadge sourceType={policy.sourceType} sourceLabel={policy.sourceLabel} size="sm" />
  {/* ... 이후 dDay + 북마크 버튼 동일 ... */}
</div>
```

기존 카드 하단 메타 영역의 `getRegionName(...)` 노출은 정보 중복이지만 그대로 유지 (지도 아이콘 옆 라벨). 시각적 위계가 다르므로 중복으로 보이지 않음.

- [ ] **Step 2: 타입 체크 + 빌드**

```bash
npx tsc --noEmit
npm run build
```
Expected: 0 errors

- [ ] **Step 3: 브라우저 검증**

```bash
npm run dev
```

http://localhost:5173/policies 에서:
- [ ] 전국 정책 카드 상단에 회색 "전국" 뱃지.
- [ ] 서울 강남구 정책 카드 상단에 노란 "강남구" 또는 "강남구 +N" 뱃지.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/policy/PolicyCard.tsx
git commit -m "$(cat <<'EOF'
feat(frontend): PolicyCard 상단에 지역 뱃지 추가

전국 정책 → 회색 뱃지, 시·군·구 정책 → 노란 뱃지로 출처 구분.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 16: 임시 라우트 삭제 + 전체 회귀 검증 + PR

**Files:**
- Modify: `frontend/src/App.tsx`
- Delete: `frontend/src/pages/_dev/RegionPickerPlayground.tsx`

- [ ] **Step 1: 임시 라우트 + 페이지 삭제**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
rm frontend/src/pages/_dev/RegionPickerPlayground.tsx
rmdir frontend/src/pages/_dev   # 비어 있으면
```

`App.tsx` 에서:
- `import RegionPickerPlayground from '@/pages/_dev/RegionPickerPlayground';` 삭제
- `<Route path="/_dev/region-picker" element={<RegionPickerPlayground />} />` 삭제

- [ ] **Step 2: 백엔드 + 프론트 전체 검증**

```bash
cd backend
./gradlew test
cd ../frontend
npm run test
npm run build
```
Expected: 모두 PASS

- [ ] **Step 3: 스펙 §8.2 + §8.3 수동 체크리스트 완주**

브라우저 (모바일 + 데스크톱 viewport 둘 다) 에서 확인:

기능:
- [ ] 비로그인 첫 진입: 트리거 "전체", 배너 없음.
- [ ] 로그인 + `interestRegions=['SEOUL']`: URL `?regions=11` replace, 배너 1회 노출, 서울 + 전국 정책만.
- [ ] 배너 "해제" 클릭: `regions` 빈 상태, 새로고침해도 자동 적용 안 됨.
- [ ] picker 모바일: 서울 → 강남·마포 체크 → URL `?regions=11680,11440`.
- [ ] picker 데스크톱: 트리거 클릭 → 460px 팝오버.
- [ ] "전국만": 좌측 "전국" → URL `?regions=NATIONWIDE`, 시·군·구 정책 미노출.
- [ ] 카드 뱃지: 전국→회색, 강남구→노란 "강남구".
- [ ] 검색 모드: 키워드 입력 → 트리거 disabled + 헬퍼.
- [ ] 잘못된 URL `?regions=99999`: 빈 필터로 동작 (전체 정책).
- [ ] 페이지네이션 + 다른 필터 조합.
- [ ] 브라우저 뒤로가기.

접근성:
- [ ] 키보드만으로 picker 열기 → 시·도 선택 → 시·군·구 체크 → 적용.
- [ ] 트리거 칩 `aria-label` 에 선택 개수 포함.
- [ ] 자동 적용 배너 `role="status"` 1회 안내.

- [ ] **Step 4: 커밋 + 푸시**

```bash
git add frontend/src/App.tsx
git rm frontend/src/pages/_dev/RegionPickerPlayground.tsx
git commit -m "$(cat <<'EOF'
chore(frontend): 임시 /_dev/region-picker 라우트 제거

PolicyListPage 통합 완료. 단독 미리보기 페이지 더 이상 필요 없음.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"

git push -u origin feat/policy-region-filter
```

- [ ] **Step 5: PR 생성**

```bash
gh pr create --title "feat(policy): 지역 필터 — 시·도/시·군·구 드릴다운 + 다중 선택" --body "$(cat <<'EOF'
## Summary

- **2-컬럼 RegionPicker** 도입: 좌측 시·도, 우측 시·군·구 체크박스. 모바일 풀스크린 시트 / 데스크톱 460px 팝오버.
- 다중 선택, "전국" 정책 항상 OR 포함, "전국만 보기" 모드 (`?regions=NATIONWIDE`).
- 로그인 사용자 `NotificationSetting.interestRegions[0]` 을 시·도 행정코드로 매핑해 자동 적용 + 배너 해제 가능.
- 백엔드: `GET /api/v1/regions` 마스터 API 신설, `PolicySpecification` 콤마 패딩 LIKE 로 false-positive 회피.
- URL: `?regions=11680,11440` CSV (행정코드). `?regionCode=SEOUL` legacy 는 정규화 후 무시.

스펙: `docs/superpowers/specs/2026-05-21-policy-region-filter-design.md`

## Test plan
- [x] 백엔드 단위 테스트: RegionFilter, PolicySpecification, PolicyQueryService, PolicyController, RegionQueryService, RegionController, JsonRegionCodeRegistry — 모두 PASS.
- [x] 프론트 단위 테스트: regionFilter, region(labels), RegionPickerBanner — 모두 PASS.
- [x] 프론트 typecheck + build — PASS.
- [x] 수동 검증: 스펙 §8.2 + §8.3 체크리스트 완주.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review (계획 작성자용)

- [x] Spec §2 목표 6개 모두 task 로 매핑됨: 드릴다운(T12) / 다중 선택(T12) / 전국 OR(T2,T12) / 자동 적용(T14) / URL CSV(T7,T9,T14) / 카드 뱃지(T15).
- [x] Spec §4.5 매칭 규칙: T1 (RegionFilter 분류) + T2 (PolicySpecification 콤마 패딩) 으로 분담.
- [x] Spec §5.1 Region API: T6 통째로 커버.
- [x] Spec §6.1-6.6 프론트 변경: T7~T15 분산.
- [x] Placeholder 없음 (모든 코드 블록에 실제 코드).
- [x] 시그니처 일관성: `RegionFilter` 가 Task 1 부터 마지막까지 동일 API (`isActive()`, `isNationwideOnly()`, `sidoCodes()`, `sigunguCodes()`, `of(List<String>)`, `ofCsv(String)`).
- [x] 프론트 시그니처 일관성: `parseRegionsParam` / `toRegionsParam` / `classifyRegionCodes` / `NATIONWIDE_TOKEN` 이름이 T7, T11, T12, T14 에서 동일.
- [x] 검색 API 의 지역 필터 미포함은 Spec §3 Non-Goals 와 일치 (의도된 누락).
