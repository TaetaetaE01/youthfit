# 사실상 상시 정책 캘린더 분류 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 신청기간이 `?-12-31` 로 끝나면서 약 9 개월 이상인 정책을 캘린더 막대 대신 항상모집 섹션에 표시. DB 원본은 유지.

**Architecture:** 도메인 메서드 `Policy.isEffectivelyAlwaysOpen()` 로 판정. `/calendar` 응답은 Application 레이어에서 필터링, `/calendar/always-open` 응답은 JPA Specification 의 OR 조건으로 확장. 응답 DTO 에 `deadlineYear` 추가해 프론트가 라벨 분기.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JPA Criteria, JUnit 5, Mockito · React 19, TanStack Query, TypeScript, Vitest

**Spec:** `docs/superpowers/specs/2026-05-27-effectively-always-open-calendar-design.md`

---

## File Structure

### 백엔드 (생성/수정)
- 수정: `backend/src/main/java/com/youthfit/policy/domain/model/Policy.java` — 도메인 메서드
- 수정: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java` — `alwaysOpen` OR 확장
- 수정: `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java` — `findByDateRange` 필터
- 수정: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyCalendarResult.java` — `deadlineYear` 필드
- 수정: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyCalendarResponse.java` — `deadlineYear` 필드
- 생성: `backend/src/test/java/com/youthfit/policy/domain/model/PolicyTest.java` — `isEffectivelyAlwaysOpen` 단위 테스트
- 수정: `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java` — `findByDateRange` 필터링 테스트 추가

### 프론트엔드 (수정)
- 수정: `frontend/src/types/policy.ts` — `PolicyCalendarItem.deadlineYear?: number`
- 수정: `frontend/src/components/policy-calendar/AlwaysOpenSection.tsx` — 라벨 분기

---

## Task 1: Policy 도메인 메서드 `isEffectivelyAlwaysOpen()` + 단위 테스트

**Files:**
- Create: `backend/src/test/java/com/youthfit/policy/domain/model/PolicyTest.java`
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/Policy.java` (마지막 메서드 직후 추가)

- [ ] **Step 1: 실패하는 테스트 작성**

새 파일 `backend/src/test/java/com/youthfit/policy/domain/model/PolicyTest.java`:

```java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyTest {

    private Policy policyWith(LocalDate start, LocalDate end) {
        return Policy.builder()
                .title("t").summary("s")
                .category(Category.EMPLOYMENT).regionCode("전국")
                .applyStart(start)
                .applyEnd(end)
                .build();
    }

    @Test
    @DisplayName("isEffectivelyAlwaysOpen — end 가 12-31 이고 start 가 같은 해 1-1 이면 true (365일)")
    void effectivelyAlwaysOpen_fullYear() {
        Policy p = policyWith(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(p.isEffectivelyAlwaysOpen()).isTrue();
    }

    @Test
    @DisplayName("isEffectivelyAlwaysOpen — end 가 12-31 이고 start 가 null 이면 true")
    void effectivelyAlwaysOpen_startNull() {
        Policy p = policyWith(null, LocalDate.of(2026, 12, 31));
        assertThat(p.isEffectivelyAlwaysOpen()).isTrue();
    }

    @Test
    @DisplayName("isEffectivelyAlwaysOpen — end 가 12-31 이고 span 이 정확히 270일이면 true")
    void effectivelyAlwaysOpen_exactly270Days() {
        LocalDate end = LocalDate.of(2026, 12, 31);
        LocalDate start = end.minusDays(270);
        Policy p = policyWith(start, end);
        assertThat(p.isEffectivelyAlwaysOpen()).isTrue();
    }

    @Test
    @DisplayName("isEffectivelyAlwaysOpen — end 가 12-31 이고 span 이 269일이면 false (단기 모집)")
    void effectivelyAlwaysOpen_below270Days() {
        LocalDate end = LocalDate.of(2026, 12, 31);
        LocalDate start = end.minusDays(269);
        Policy p = policyWith(start, end);
        assertThat(p.isEffectivelyAlwaysOpen()).isFalse();
    }

    @Test
    @DisplayName("isEffectivelyAlwaysOpen — end 가 12-31 이 아니면 false (예: 11-30)")
    void effectivelyAlwaysOpen_endNot1231() {
        Policy p = policyWith(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 30));
        assertThat(p.isEffectivelyAlwaysOpen()).isFalse();
    }

    @Test
    @DisplayName("isEffectivelyAlwaysOpen — end 가 null 이면 false (이 메서드는 사실상 상시만 책임)")
    void effectivelyAlwaysOpen_endNull() {
        Policy p = policyWith(null, null);
        assertThat(p.isEffectivelyAlwaysOpen()).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend
./gradlew test --tests "com.youthfit.policy.domain.model.PolicyTest"
```
예상: 컴파일 실패 (메서드 미존재) 또는 `cannot find symbol: method isEffectivelyAlwaysOpen()`.

- [ ] **Step 3: `Policy` 에 도메인 메서드 추가**

`Policy.java` 의 `isExpired()` 메서드 (line 261 근처) 바로 아래에 추가. import 가 이미 `java.time.LocalDate` 있고, `java.time.temporal.ChronoUnit` 만 새로 필요.

import 섹션에 추가:
```java
import java.time.temporal.ChronoUnit;
```

`isExpired()` 직후에 메서드 추가:
```java
    /**
     * 캘린더 표시에서 "사실상 상시" 로 분류할지 판정.
     * end 가 ?-12-31 이고 신청 가능 기간이 약 9개월 (270일) 이상이면 true.
     * 진짜 상시 (start, end 모두 null) 는 이 메서드의 책임이 아니다.
     */
    public boolean isEffectivelyAlwaysOpen() {
        if (applyEnd == null) return false;
        if (applyEnd.getMonthValue() != 12 || applyEnd.getDayOfMonth() != 31) return false;
        if (applyStart == null) return true;
        return ChronoUnit.DAYS.between(applyStart, applyEnd) >= 270;
    }
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.youthfit.policy.domain.model.PolicyTest"
```
예상: 6 개 테스트 모두 PASS.

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/policy/domain/model/Policy.java backend/src/test/java/com/youthfit/policy/domain/model/PolicyTest.java
git commit -m "$(cat <<'EOF'
feat(policy): Policy.isEffectivelyAlwaysOpen 도메인 메서드 추가

end 가 ?-12-31 이고 신청 기간이 270일 이상이면
사실상 상시 정책으로 분류.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `PolicyQueryService.findByDateRange` 필터링 + 테스트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java:107-110`
- Modify: `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java` (테스트 추가)

- [ ] **Step 1: 실패하는 테스트 작성**

`PolicyQueryServiceTest.java` 에서 `findByDateRange_success()` 테스트 (line 237) 바로 아래에 추가:

```java
    @Test
    @DisplayName("findByDateRange — 사실상 상시 정책 (end=12-31, span≥270일) 은 결과에서 제외")
    void findByDateRange_excludesEffectivelyAlwaysOpen() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        Policy normal = Policy.builder()
                .title("3월 청년월세").summary("s")
                .category(Category.HOUSING).regionCode("전국")
                .applyStart(LocalDate.of(2026, 3, 10))
                .applyEnd(LocalDate.of(2026, 3, 20))
                .build();
        ReflectionTestUtils.setField(normal, "id", 1L);

        Policy effectivelyAlways = Policy.builder()
                .title("연중 멘토링").summary("s")
                .category(Category.EDUCATION).regionCode("전국")
                .applyStart(LocalDate.of(2026, 1, 1))
                .applyEnd(LocalDate.of(2026, 12, 31))
                .build();
        ReflectionTestUtils.setField(effectivelyAlways, "id", 2L);

        when(policyRepository.findByCalendarRange(eq(from), eq(to), any(), eq(null)))
                .thenReturn(List.of(normal, effectivelyAlways));

        List<PolicyCalendarResult> result =
                policyQueryService.findByDateRange(from, to, RegionFilter.of(null), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend
./gradlew test --tests "com.youthfit.policy.application.service.PolicyQueryServiceTest.findByDateRange_excludesEffectivelyAlwaysOpen"
```
예상: FAIL — `Expected size: 1 but was: 2`.

- [ ] **Step 3: 필터 추가**

`PolicyQueryService.java` line 107-110 의 stream 체인을 수정. 현재:

```java
        return policyRepository.findByCalendarRange(from, to, regionFilter, category)
                .stream()
                .map(p -> PolicyCalendarResult.from(p, resolveRegionLabel(p)))
                .toList();
```

다음으로 변경:

```java
        return policyRepository.findByCalendarRange(from, to, regionFilter, category)
                .stream()
                .filter(p -> !p.isEffectivelyAlwaysOpen())
                .map(p -> PolicyCalendarResult.from(p, resolveRegionLabel(p)))
                .toList();
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.youthfit.policy.application.service.PolicyQueryServiceTest"
```
예상: 모든 PolicyQueryServiceTest 테스트 PASS (기존 + 새 1 개).

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java
git commit -m "$(cat <<'EOF'
feat(policy): /calendar 응답에서 사실상 상시 정책 제외

PolicyQueryService.findByDateRange 에서 isEffectivelyAlwaysOpen
필터 적용. DB 원본은 유지.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `PolicySpecification.alwaysOpen` OR 조건 확장

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java:125-157`

이 작업은 DB Specification 변경이므로 단위 테스트만으로는 검증이 어렵다. 통합 테스트 또는 직접 SQL 검증이 필요하지만, 이 코드베이스에 PolicyRepository 통합 테스트 인프라가 없으므로 (`find` 결과 없음), Task 6 의 수동 검증으로 대체한다.

**설계 노트:**
- 진짜 상시: `applyStart IS NULL AND applyEnd IS NULL`
- 사실상 상시: end 의 month=12, day=31 이고 (start null 이거나 span >= 270 일)
- Span 계산: Hibernate 6 의 `day_of_year`, `year` 함수로 day-of-year 추출. 같은 해면 doy 차이가 270 이상, 다른 해면 자동 만족.

- [ ] **Step 1: `alwaysOpen` Specification 수정**

`PolicySpecification.java` line 125-157 의 `alwaysOpen` 메서드를 다음으로 통째 교체:

```java
    public static Specification<Policy> alwaysOpen(RegionFilter regionFilter, Category category) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Path<LocalDate> applyStart = root.get("applyStart");
            Path<LocalDate> applyEnd = root.get("applyEnd");

            // 진짜 상시
            Predicate trueAlwaysOpen = cb.and(
                    cb.isNull(applyStart),
                    cb.isNull(applyEnd)
            );

            // 사실상 상시: end month=12, day=31 이고 (start null 이거나 span >= 270 일)
            Expression<Integer> endMonth = cb.function("month", Integer.class, applyEnd);
            Expression<Integer> endDay = cb.function("day_of_month", Integer.class, applyEnd);
            Expression<Integer> endYear = cb.function("year", Integer.class, applyEnd);
            Expression<Integer> startYear = cb.function("year", Integer.class, applyStart);
            Expression<Integer> endDoy = cb.function("day_of_year", Integer.class, applyEnd);
            Expression<Integer> startDoy = cb.function("day_of_year", Integer.class, applyStart);

            Predicate endIsDec31 = cb.and(
                    cb.equal(endMonth, 12),
                    cb.equal(endDay, 31)
            );

            // 같은 해면 doy 차이 >= 270, 다른 해면 (start.year < end.year) 자동 만족
            Predicate sameYearLongSpan = cb.and(
                    cb.equal(startYear, endYear),
                    cb.greaterThanOrEqualTo(cb.diff(endDoy, startDoy), 270)
            );
            Predicate multiYear = cb.lessThan(startYear, endYear);
            Predicate spanLongEnough = cb.or(sameYearLongSpan, multiYear);

            Predicate effectivelyAlwaysOpen = cb.and(
                    cb.isNotNull(applyEnd),
                    endIsDec31,
                    cb.or(cb.isNull(applyStart), spanLongEnough)
            );

            predicates.add(cb.or(trueAlwaysOpen, effectivelyAlwaysOpen));

            // 만료된 정책 제외: referenceYear < currentYear 면 effective status 가 CLOSED.
            int currentYear = LocalDate.now().getYear();
            Path<Integer> referenceYear = root.get("referenceYear");
            predicates.add(cb.or(
                    cb.isNull(referenceYear),
                    cb.greaterThanOrEqualTo(referenceYear, currentYear)
            ));

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

상단 import 에 `jakarta.persistence.criteria.Expression` 가 이미 있으므로 추가 import 불필요.

- [ ] **Step 2: 컴파일 통과 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend
./gradlew compileJava
```
예상: BUILD SUCCESSFUL.

- [ ] **Step 3: 기존 테스트 회귀 확인**

```bash
./gradlew test --tests "com.youthfit.policy.application.service.PolicyQueryServiceTest"
```
예상: 모든 테스트 PASS (Mockito 가 Repository 를 모킹하므로 Specification 변경이 영향 안 미침).

- [ ] **Step 4: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySpecification.java
git commit -m "$(cat <<'EOF'
feat(policy): alwaysOpen Specification 에 사실상 상시 OR 조건 추가

end day-of-year >= 365 이고 (start null 이거나 span >= 270일)
조건을 PostgreSQL extract(doy/year) 함수로 표현.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 응답 DTO 에 `deadlineYear` 필드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyCalendarResult.java`
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyCalendarResponse.java`

- [ ] **Step 1: `PolicyCalendarResult` 에 필드 추가**

`PolicyCalendarResult.java` 전체를 다음으로 교체:

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
        String regionLabel,
        Integer deadlineYear
) {
    public static PolicyCalendarResult from(Policy policy, String regionLabel) {
        Integer deadlineYear = policy.isEffectivelyAlwaysOpen()
                ? policy.getApplyEnd().getYear()
                : null;
        return new PolicyCalendarResult(
                policy.getId(),
                policy.getTitle(),
                policy.getCategory(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                regionLabel,
                deadlineYear
        );
    }
}
```

- [ ] **Step 2: `PolicyCalendarResponse` 에 필드 추가**

`PolicyCalendarResponse.java` 전체를 다음으로 교체:

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
        String regionLabel,
        Integer deadlineYear
) {
    public static PolicyCalendarResponse from(PolicyCalendarResult result) {
        return new PolicyCalendarResponse(
                result.id(),
                result.title(),
                result.category(),
                result.applyStart(),
                result.applyEnd(),
                result.regionLabel(),
                result.deadlineYear()
        );
    }
}
```

- [ ] **Step 3: 컴파일 + 전체 테스트 회귀 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend
./gradlew test
```
예상: 모든 테스트 PASS. `findAlwaysOpen_success` 같은 기존 테스트도 `deadlineYear` 필드 추가에 영향받지 않음 (record 추가 필드는 default 검증 없음).

- [ ] **Step 4: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyCalendarResult.java backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyCalendarResponse.java
git commit -m "$(cat <<'EOF'
feat(policy): PolicyCalendarResult/Response 에 deadlineYear 추가

isEffectivelyAlwaysOpen 이면 end.year, 아니면 null.
프론트가 '26년 상시모집' / '상시모집' 라벨 분기에 사용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 프론트엔드 타입 + `AlwaysOpenSection` 라벨 표시

**Files:**
- Modify: `frontend/src/types/policy.ts:353-360`
- Modify: `frontend/src/components/policy-calendar/AlwaysOpenSection.tsx:43-53`

- [ ] **Step 1: `PolicyCalendarItem` 타입에 `deadlineYear` 추가**

`frontend/src/types/policy.ts` line 353-360 의 `PolicyCalendarItem` 타입 정의:

기존:
```typescript
export type PolicyCalendarItem = {
  id: number;
  title: string;
  category: PolicyCategory;
  applyStart: string | null;   // YYYY-MM-DD
  applyEnd: string | null;     // YYYY-MM-DD
  regionLabel: string;
};
```

수정:
```typescript
export type PolicyCalendarItem = {
  id: number;
  title: string;
  category: PolicyCategory;
  applyStart: string | null;   // YYYY-MM-DD
  applyEnd: string | null;     // YYYY-MM-DD
  regionLabel: string;
  deadlineYear: number | null;
};
```

- [ ] **Step 2: `AlwaysOpenSection` 라벨 분기 추가**

`frontend/src/components/policy-calendar/AlwaysOpenSection.tsx` line 43-53 의 `<div>` 블록을 다음으로 교체:

기존:
```tsx
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
```

수정:
```tsx
      <div className="flex flex-wrap gap-2">
        {data.content.map((it) => {
          const periodLabel = it.deadlineYear
            ? `${String(it.deadlineYear).slice(2)}년 상시`
            : '상시';
          return (
            <Link
              key={it.id}
              to={`/policies/${it.id}`}
              className="flex items-center gap-1.5 rounded-full border border-neutral-200 bg-neutral-50 px-3 py-1.5 text-xs font-medium text-neutral-700 hover:bg-neutral-100"
            >
              <span className="rounded-full bg-brand-100 px-1.5 py-0.5 text-[10px] font-semibold text-brand-800">
                {periodLabel}
              </span>
              <span>{it.title}</span>
            </Link>
          );
        })}
      </div>
```

- [ ] **Step 3: 빌드 + 타입체크 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/frontend
npm run build
```
예상: BUILD SUCCESSFUL, type 오류 없음.

- [ ] **Step 4: 단위 테스트 실행 (관련 테스트만)**

```bash
npm run test -- --run
```
예상: PASS (기존 calendarLayout 테스트 등 영향 없음).

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add frontend/src/types/policy.ts frontend/src/components/policy-calendar/AlwaysOpenSection.tsx
git commit -m "$(cat <<'EOF'
feat(frontend): 항상모집 섹션에 '26년 상시' 라벨 표시

deadlineYear 가 있으면 'YY년 상시', 없으면 '상시'.
PolicyCalendarItem 에 deadlineYear: number | null 추가.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: 통합 검증 (수동)

**Files:** 변경 없음. 동작 검증만.

- [ ] **Step 1: 백엔드 부팅**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend
./gradlew bootRun
```
예상: 8080 포트 부팅, 에러 없음.

- [ ] **Step 2: `/calendar` 응답에 사실상 상시 정책이 없는지 확인**

DB 에 `apply_end = '2026-12-31'` 이고 `apply_start = '2026-01-01'` 인 정책이 있어야 검증 가능. 없으면 `db/init.sql` 또는 직접 INSERT 로 만들거나, 기존 정책 한 건을 UPDATE.

```bash
# 응답 확인 (March 2026)
curl -s "http://localhost:8080/api/v1/policies/calendar?from=2026-03-01&to=2026-03-31" | jq '.items | map(select(.applyEnd == "2026-12-31"))'
```
예상: 빈 배열 `[]`.

- [ ] **Step 3: `/calendar/always-open` 응답에 사실상 상시 + `deadlineYear` 가 있는지 확인**

```bash
curl -s "http://localhost:8080/api/v1/policies/calendar/always-open" | jq '.content[] | {title, applyStart, applyEnd, deadlineYear}'
```
예상: `apply_end='2026-12-31'` 인 정책이 `deadlineYear: 2026` 으로 노출. 진짜 상시 (둘 다 null) 는 `deadlineYear: null`.

- [ ] **Step 4: 프론트 부팅 + 캘린더 페이지 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/frontend
npm run dev
```
브라우저로 캘린더 페이지 (예: `http://localhost:5173/policies/calendar`) 방문:
- 1 월 ~ 12 월 통째 막대가 사라졌는지 확인
- "상시 모집 정책" 섹션에서 "26 년 상시" 칩이 표시되는지 확인
- 진짜 상시 정책은 "상시" 칩으로 표시되는지 확인

- [ ] **Step 5: 정책 상세 화면에서 원본 기간 유지 확인**

해당 정책의 상세 페이지를 직접 방문하거나 API 호출:
```bash
curl -s "http://localhost:8080/api/v1/policies/{id}" | jq '{applyStart, applyEnd}'
```
예상: DB 원본 값 그대로 (예: `applyStart=2026-01-01, applyEnd=2026-12-31`).

- [ ] **Step 6: 전체 백엔드 테스트 회귀 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend
./gradlew test
```
예상: 모든 테스트 PASS.

- [ ] **Step 7: 검증 통과 시 추가 커밋 없음 (Task 1~5 의 커밋들로 완료)**

검증 단계라 커밋 불필요. 만약 수동 검증에서 버그 발견 시 해당 Task 로 돌아가 수정 후 별도 커밋.

---

## 완료 기준

- [ ] 모든 Task 1~6 의 체크박스 완료
- [ ] `./gradlew test` 전체 PASS
- [ ] `npm run build` SUCCESS
- [ ] 캘린더 화면에서 1 년 통째 막대 사라짐 (수동 확인)
- [ ] 항상모집 섹션에 "YY 년 상시" 칩이 표시됨
- [ ] 정책 상세에는 원본 `applyStart/applyEnd` 유지
