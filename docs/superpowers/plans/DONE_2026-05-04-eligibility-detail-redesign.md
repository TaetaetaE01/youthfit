# 정책 상세 적합도 카드 사용자 친화 개편 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책 상세 페이지의 적합도 카드를 사용자 친화적으로 재설계 — raw enum 대신 한국어 표시, "정책 요구 vs 내 정보" 비교, 결과별 그룹핑, 신뢰도 배지, 누락 정보 CTA → 프로필 페이지 라우팅.

**Architecture:**
- 백엔드: 도메인 enum에 `displayName` 필드 추가 + `RequirementFormatter`/`UserValueFormatter`/`VerdictTextGenerator`/`SummaryHeadlineGenerator` 신규. `EligibilityService`가 평가 후 그룹핑·요약 생성. 응답 DTO는 `RequirementView`/`UserValueView`/`SourceView`/`GroupedCriteria`/`SummaryView`로 재구성.
- 프론트엔드: `PolicyDetailPage` 인라인 적합도 카드를 `components/policy/eligibility/` 5개 컴포넌트로 분해. `MyPage`에 `?focus={field}` 쿼리 처리 추가, `EligibilityInfoCard`가 초기 오픈 row 받아 자동 스크롤·전개.

**Tech Stack:**
- Backend: Java 21, Spring Boot 4, JUnit 5, Mockito, AssertJ
- Frontend: React 19, TypeScript 5, Vite, TanStack Query, React Router v7, Tailwind v4, Vitest

**Spec:** `docs/superpowers/specs/2026-05-04-eligibility-detail-redesign-design.md`

---

## File Structure

### 신규 파일

**Backend (eligibility 모듈)**
- `eligibility/domain/model/UncertainReason.java` — UNCERTAIN 사유 enum
- `eligibility/domain/model/view/RequirementView.java` — `(operator, displayText)`
- `eligibility/domain/model/view/UserValueView.java` — `(raw, displayText)`
- `eligibility/domain/model/view/SourceView.java` — `(snippet)`
- `eligibility/domain/model/view/GroupedCriteria.java` — 결과별 분류
- `eligibility/domain/model/view/SummaryView.java` — `(headline, eligibleCount, uncertainCount, ineligibleCount)`
- `eligibility/domain/service/RequirementFormatter.java` — rule → 한국어 displayText
- `eligibility/domain/service/UserValueFormatter.java` — raw 값 → `UserValueView`
- `eligibility/domain/service/VerdictTextGenerator.java` — 자연어 한 줄 생성
- `eligibility/application/service/SummaryHeadlineGenerator.java` — 요약 헤드라인 생성

**Backend tests**
- `eligibility/domain/service/RequirementFormatterTest.java`
- `eligibility/domain/service/UserValueFormatterTest.java`
- `eligibility/domain/service/VerdictTextGeneratorTest.java`
- `eligibility/application/service/SummaryHeadlineGeneratorTest.java`

**Frontend (eligibility UI)**
- `frontend/src/components/policy/eligibility/EligibilityCard.tsx`
- `frontend/src/components/policy/eligibility/EligibilityHeader.tsx`
- `frontend/src/components/policy/eligibility/CriterionGroup.tsx`
- `frontend/src/components/policy/eligibility/CriterionRow.tsx`
- `frontend/src/components/policy/eligibility/EligibilityFooter.tsx`
- `frontend/src/components/policy/eligibility/eligibilityStyles.ts` — 결과별 색상·라벨 토큰
- `frontend/src/components/policy/eligibility/fieldToRowKey.ts` — 적합도 필드 → MyPage Row 키 매핑

### 수정 파일

**Backend**
- `user/domain/model/EmploymentKind.java` — `displayName` 필드 추가
- `user/domain/model/MaritalStatus.java`
- `user/domain/model/Education.java`
- `user/domain/model/MajorField.java`
- `user/domain/model/SpecializationField.java`
- `eligibility/domain/service/CriterionEvaluation.java` — record 재설계
- `eligibility/domain/service/EligibilityEvaluator.java` — 헬퍼 시그니처 변경
- `eligibility/application/dto/result/CriterionResult.java` — record 재설계
- `eligibility/application/dto/result/EligibilityJudgmentResult.java` — record 재설계
- `eligibility/application/service/EligibilityService.java` — 그룹핑·요약 생성 로직
- `eligibility/presentation/dto/response/CriterionResponse.java` — record 재설계
- `eligibility/presentation/dto/response/EligibilityJudgmentResponse.java` — record 재설계
- `eligibility/domain/service/CriterionEvaluationTest.java` — 새 시그니처 반영
- `eligibility/application/service/EligibilityServiceTest.java` — 새 응답 구조 반영

**Frontend**
- `frontend/src/types/policy.ts` — `CriterionItem`/`EligibilityResponse` 재정의 + 신규 인터페이스
- `frontend/src/pages/PolicyDetailPage.tsx` — 인라인 적합도 카드 제거, 신규 컴포넌트 import
- `frontend/src/pages/MyPage.tsx` — `useSearchParams`로 `focus` 처리, scroll target ref
- `frontend/src/components/personal-info/EligibilityInfoCard.tsx` — `initialOpen` prop, Row id 부여

---

## Task 1: 도메인 enum에 한국어 displayName 추가 (5개 enum)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/domain/model/EmploymentKind.java`
- Modify: `backend/src/main/java/com/youthfit/user/domain/model/MaritalStatus.java`
- Modify: `backend/src/main/java/com/youthfit/user/domain/model/Education.java`
- Modify: `backend/src/main/java/com/youthfit/user/domain/model/MajorField.java`
- Modify: `backend/src/main/java/com/youthfit/user/domain/model/SpecializationField.java`

라벨은 프론트의 `frontend/src/types/personalInfo.ts` 의 `*_LABELS` 상수와 동일하게 맞춥니다. (이전 인용: `MARITAL_STATUS_LABELS`, `EDUCATION_LABELS`, `EMPLOYMENT_KIND_LABELS`, `MAJOR_FIELD_LABELS`, `SPECIALIZATION_LABELS`)

- [ ] **Step 1: EmploymentKind 수정**

```java
package com.youthfit.user.domain.model;

public enum EmploymentKind {
    EMPLOYEE("직장인"),
    SELF_EMPLOYED("자영업"),
    UNEMPLOYED("미취업"),
    FREELANCER("프리랜서"),
    DAILY_WORKER("일용직"),
    ENTREPRENEUR("창업가"),
    PART_TIME("아르바이트"),
    FARMER("농업인"),
    OTHER("기타");

    private final String displayName;

    EmploymentKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

- [ ] **Step 2: MaritalStatus 수정**

```java
package com.youthfit.user.domain.model;

public enum MaritalStatus {
    MARRIED("기혼"),
    SINGLE("미혼");

    private final String displayName;

    MaritalStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

- [ ] **Step 3: Education 수정**

```java
package com.youthfit.user.domain.model;

public enum Education {
    UNDER_HIGH("고졸 미만"),
    HIGH_SCHOOL_IN("고등학교 재학"),
    HIGH_SCHOOL_EXPECTED("고등학교 졸업 예정"),
    HIGH_SCHOOL_GRAD("고등학교 졸업"),
    COLLEGE_IN("대학 재학"),
    COLLEGE_EXPECTED("대학 졸업 예정"),
    COLLEGE_GRAD("대학 졸업"),
    GRADUATE("대학원 이상"),
    OTHER("기타");

    private final String displayName;

    Education(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

- [ ] **Step 4: MajorField 수정**

```java
package com.youthfit.user.domain.model;

public enum MajorField {
    HUMANITIES("인문계열"),
    SOCIAL("사회계열"),
    ECONOMICS("상경계열"),
    NATURAL("자연계열"),
    ENGINEERING("공학계열"),
    ARTS("예체능계열"),
    AGRICULTURE("농수산해양계열"),
    OTHER("기타");

    private final String displayName;

    MajorField(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

- [ ] **Step 5: SpecializationField 수정**

```java
package com.youthfit.user.domain.model;

public enum SpecializationField {
    SME("중소기업"),
    WOMAN("여성"),
    BASIC_LIVELIHOOD("기초생활수급자"),
    SINGLE_PARENT("한부모가정"),
    DISABLED("장애인"),
    FARMER("농업인"),
    MILITARY("군 복무"),
    LOCAL_TALENT("지역인재"),
    OTHER("기타");

    private final String displayName;

    SpecializationField(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

- [ ] **Step 6: 빌드로 회귀 확인**

Run:
```bash
cd backend && ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL. (단순 enum 메서드 추가라 기존 호출부에 영향 없음.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/youthfit/user/domain/model/EmploymentKind.java \
        backend/src/main/java/com/youthfit/user/domain/model/MaritalStatus.java \
        backend/src/main/java/com/youthfit/user/domain/model/Education.java \
        backend/src/main/java/com/youthfit/user/domain/model/MajorField.java \
        backend/src/main/java/com/youthfit/user/domain/model/SpecializationField.java
git commit -m "feat(user): 도메인 enum에 한국어 displayName 추가"
```

---

## Task 2: UncertainReason enum + view 레코드 5개 신규

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/model/UncertainReason.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/model/view/RequirementView.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/model/view/UserValueView.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/model/view/SourceView.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/model/view/GroupedCriteria.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/model/view/SummaryView.java`

도메인 모델 안에 표시용 값 객체 패키지(`view`)를 둡니다. presentation 의존이 아닌 도메인 표현 모델로 취급합니다 (Spring/JPA 의존 없음).

- [ ] **Step 1: UncertainReason 생성**

```java
package com.youthfit.eligibility.domain.model;

public enum UncertainReason {
    MISSING_FIELD,
    AMBIGUOUS_SOURCE
}
```

- [ ] **Step 2: RequirementView 생성**

```java
package com.youthfit.eligibility.domain.model.view;

public record RequirementView(String operator, String displayText) {}
```

- [ ] **Step 3: UserValueView 생성**

```java
package com.youthfit.eligibility.domain.model.view;

public record UserValueView(String raw, String displayText) {}
```

- [ ] **Step 4: SourceView 생성**

```java
package com.youthfit.eligibility.domain.model.view;

public record SourceView(String snippet) {}
```

- [ ] **Step 5: SummaryView 생성**

```java
package com.youthfit.eligibility.domain.model.view;

public record SummaryView(
        String headline,
        int eligibleCount,
        int uncertainCount,
        int ineligibleCount
) {}
```

- [ ] **Step 6: GroupedCriteria 생성 (CriterionResult import 전이라 List<?> 사용)**

GroupedCriteria는 `CriterionResult`를 담아야 하지만, 현재 `CriterionResult`가 record라 application 패키지에 있습니다. 도메인 표현 모델이 application 모델을 import하면 의존 방향 위반이라 **GroupedCriteria는 application/dto/result 아래에 둡니다.** Step 6은 건너뜁니다 (Task 7에서 GroupedCriteria를 application 패키지에 생성).

- [ ] **Step 7: 빌드로 회귀 확인**

Run:
```bash
cd backend && ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/model/UncertainReason.java \
        backend/src/main/java/com/youthfit/eligibility/domain/model/view/
git commit -m "feat(eligibility): UncertainReason enum + 표시용 view 값 객체 추가"
```

---

## Task 3: RequirementFormatter (도메인 서비스) — TDD

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/service/RequirementFormatter.java`
- Test: `backend/src/test/java/com/youthfit/eligibility/domain/service/RequirementFormatterTest.java`

규칙(field, operator, value) → `RequirementView`. 필드별 단위·enum 매핑은 클래스 내부 상수로.

- [ ] **Step 1: 실패 테스트 작성 (BETWEEN-age)**

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequirementFormatter")
class RequirementFormatterTest {

    private final RequirementFormatter formatter = new RequirementFormatter();

    @Nested
    @DisplayName("BETWEEN 연산자")
    class Between {

        @Test
        @DisplayName("age 필드 BETWEEN 19~34 → \"만 19세 이상 34세 이하\"")
        void ageBetween() {
            RequirementView view = formatter.format("age", RuleOperator.BETWEEN, "19~34");

            assertThat(view.operator()).isEqualTo("BETWEEN");
            assertThat(view.displayText()).isEqualTo("만 19세 이상 34세 이하");
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.RequirementFormatterTest" -i
```
Expected: COMPILE FAIL — `RequirementFormatter` not found.

- [ ] **Step 3: 최소 구현 (BETWEEN-age만)**

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.view.RequirementView;

public class RequirementFormatter {

    public RequirementView format(String field, RuleOperator operator, String value) {
        String displayText = switch (operator) {
            case BETWEEN -> formatRange(field, value);
            default -> value;
        };
        return new RequirementView(operator.name(), displayText);
    }

    private String formatRange(String field, String value) {
        String[] bounds = value.split("~");
        String lo = bounds[0].trim();
        String hi = bounds[1].trim();
        if ("age".equals(field)) {
            return "만 " + lo + "세 이상 " + hi + "세 이하";
        }
        return lo + " 이상 " + hi + " 이하";
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.RequirementFormatterTest" -i
```
Expected: 1 test PASS.

- [ ] **Step 5: 추가 시나리오 테스트 작성 (EQ-enum, IN-enum, GTE-소득, LTE-소득, NOT_EQ, BETWEEN-소득)**

`RequirementFormatterTest`에 다음 `@Nested` 클래스들을 추가:

```java
    @Nested
    @DisplayName("EQ 연산자")
    class Eq {

        @Test
        @DisplayName("maritalStatus EQ MARRIED → \"기혼\"")
        void maritalEq() {
            RequirementView view = formatter.format("maritalStatus", RuleOperator.EQ, "MARRIED");
            assertThat(view.displayText()).isEqualTo("기혼");
        }

        @Test
        @DisplayName("region EQ 1100000000 → 그대로 코드 표시")
        void regionEq() {
            RequirementView view = formatter.format("region", RuleOperator.EQ, "1100000000");
            assertThat(view.displayText()).isEqualTo("1100000000");
        }
    }

    @Nested
    @DisplayName("NOT_EQ 연산자")
    class NotEq {

        @Test
        @DisplayName("maritalStatus NOT_EQ MARRIED → \"기혼 제외\"")
        void notEqEnum() {
            RequirementView view = formatter.format("maritalStatus", RuleOperator.NOT_EQ, "MARRIED");
            assertThat(view.displayText()).isEqualTo("기혼 제외");
        }
    }

    @Nested
    @DisplayName("IN 연산자")
    class In {

        @Test
        @DisplayName("employmentKind IN 다중 enum → 한국어 라벨 콤마 결합")
        void employmentIn() {
            RequirementView view = formatter.format(
                    "employmentKind",
                    RuleOperator.IN,
                    "EMPLOYEE,SELF_EMPLOYED,FREELANCER,DAILY_WORKER,PART_TIME"
            );
            assertThat(view.displayText())
                    .isEqualTo("직장인, 자영업, 프리랜서, 일용직, 아르바이트");
        }

        @Test
        @DisplayName("specializationField IN 다중 enum → 한국어 라벨")
        void specializationIn() {
            RequirementView view = formatter.format(
                    "specializationField",
                    RuleOperator.IN,
                    "SME,WOMAN"
            );
            assertThat(view.displayText()).isEqualTo("중소기업, 여성");
        }
    }

    @Nested
    @DisplayName("GTE / LTE 연산자")
    class Comparison {

        @Test
        @DisplayName("annualIncome GTE 30000000 → \"3,000만원 이상\"")
        void annualIncomeGte() {
            RequirementView view = formatter.format(
                    "annualIncome",
                    RuleOperator.GTE,
                    "30000000"
            );
            assertThat(view.displayText()).isEqualTo("3,000만원 이상");
        }

        @Test
        @DisplayName("annualIncome LTE 50000000 → \"5,000만원 이하\"")
        void annualIncomeLte() {
            RequirementView view = formatter.format(
                    "annualIncome",
                    RuleOperator.LTE,
                    "50000000"
            );
            assertThat(view.displayText()).isEqualTo("5,000만원 이하");
        }

        @Test
        @DisplayName("age GTE 19 → \"만 19세 이상\"")
        void ageGte() {
            RequirementView view = formatter.format("age", RuleOperator.GTE, "19");
            assertThat(view.displayText()).isEqualTo("만 19세 이상");
        }
    }

    @Nested
    @DisplayName("BETWEEN 소득")
    class BetweenIncome {

        @Test
        @DisplayName("annualIncome BETWEEN 20000000~50000000 → \"2,000만원 이상 5,000만원 이하\"")
        void incomeBetween() {
            RequirementView view = formatter.format(
                    "annualIncome",
                    RuleOperator.BETWEEN,
                    "20000000~50000000"
            );
            assertThat(view.displayText()).isEqualTo("2,000만원 이상 5,000만원 이하");
        }
    }
```

- [ ] **Step 6: 테스트 실패 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.RequirementFormatterTest" -i
```
Expected: 신규 테스트들 FAIL.

- [ ] **Step 7: 전체 구현으로 확장**

`RequirementFormatter.java` 전체를 다음으로 교체:

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;

import java.util.Arrays;
import java.util.stream.Collectors;

public class RequirementFormatter {

    public RequirementView format(String field, RuleOperator operator, String value) {
        String displayText = switch (operator) {
            case EQ      -> formatScalar(field, value);
            case NOT_EQ  -> formatScalar(field, value) + " 제외";
            case GTE     -> formatScalar(field, value) + " 이상";
            case LTE     -> formatScalar(field, value) + " 이하";
            case BETWEEN -> formatRange(field, value);
            case IN      -> formatList(field, value);
        };
        return new RequirementView(operator.name(), displayText);
    }

    private String formatScalar(String field, String raw) {
        return switch (field) {
            case "age" -> "만 " + raw + "세";
            case "incomeMin", "incomeMax", "annualIncome" -> formatWon(raw);
            case "maritalStatus" -> safeEnumLabel(MaritalStatus.class, raw);
            case "education", "educationLevel" -> safeEnumLabel(Education.class, raw);
            case "employmentKind", "employmentStatus" -> safeEnumLabel(EmploymentKind.class, raw);
            case "majorField" -> safeEnumLabel(MajorField.class, raw);
            case "specializationField" -> safeEnumLabel(SpecializationField.class, raw);
            default -> raw;
        };
    }

    private String formatRange(String field, String value) {
        String[] bounds = value.split("~");
        String lo = bounds[0].trim();
        String hi = bounds[1].trim();
        return formatScalar(field, lo) + " 이상 " + formatScalar(field, hi).replaceFirst("^만 ", "") + " 이하";
    }

    private String formatList(String field, String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(v -> formatScalar(field, v))
                .collect(Collectors.joining(", "));
    }

    private String formatWon(String raw) {
        try {
            long n = Long.parseLong(raw.trim());
            long manWon = n / 10_000;
            return String.format("%,d만원", manWon);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private <E extends Enum<E>> String safeEnumLabel(Class<E> enumType, String raw) {
        try {
            E value = Enum.valueOf(enumType, raw);
            return invokeDisplayName(value);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    private <E extends Enum<E>> String invokeDisplayName(E value) {
        try {
            return (String) value.getClass().getMethod("displayName").invoke(value);
        } catch (Exception e) {
            return value.name();
        }
    }
}
```

`formatRange`의 `만 ` 중복 제거 로직: `age` 필드일 때 `formatScalar("age", "19")`는 `"만 19세"`인데, 이것을 그대로 두 번 쓰면 `"만 19세 이상 만 34세 이하"`가 됩니다. 두 번째 부분에서 첫 `"만 "` 만 제거하여 `"만 19세 이상 34세 이하"`가 되도록 합니다.

- [ ] **Step 8: 전체 테스트 통과 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.RequirementFormatterTest" -i
```
Expected: 모든 테스트 PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/RequirementFormatter.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/RequirementFormatterTest.java
git commit -m "feat(eligibility): RequirementFormatter 추가 — 룰 값을 한국어 displayText로 변환"
```

---

## Task 4: UserValueFormatter (도메인 서비스) — TDD

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/service/UserValueFormatter.java`
- Test: `backend/src/test/java/com/youthfit/eligibility/domain/service/UserValueFormatterTest.java`

EligibilityEvaluator가 추출한 `Object userValue`를 사용자에게 보여줄 `{raw, displayText}` 객체로 변환.

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.view.UserValueView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserValueFormatter")
class UserValueFormatterTest {

    private final UserValueFormatter formatter = new UserValueFormatter();

    @Test
    @DisplayName("age 필드 → \"만 N세\"")
    void age() {
        UserValueView view = formatter.format("age", 29);

        assertThat(view.raw()).isEqualTo("29");
        assertThat(view.displayText()).isEqualTo("만 29세");
    }

    @Test
    @DisplayName("employmentKind 필드 (이미 enum.name() 문자열) → 한국어 라벨")
    void employment() {
        UserValueView view = formatter.format("employmentKind", "UNEMPLOYED");

        assertThat(view.raw()).isEqualTo("UNEMPLOYED");
        assertThat(view.displayText()).isEqualTo("미취업");
    }

    @Test
    @DisplayName("annualIncome 필드 → \"N만원\"")
    void income() {
        UserValueView view = formatter.format("annualIncome", 30000000L);

        assertThat(view.raw()).isEqualTo("30000000");
        assertThat(view.displayText()).isEqualTo("3,000만원");
    }

    @Test
    @DisplayName("region 필드 (코드 그대로) → 코드 그대로")
    void region() {
        UserValueView view = formatter.format("region", "1100000000");

        assertThat(view.raw()).isEqualTo("1100000000");
        assertThat(view.displayText()).isEqualTo("1100000000");
    }

    @Test
    @DisplayName("null 값 → null 반환")
    void nullValue() {
        UserValueView view = formatter.format("age", null);

        assertThat(view).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.UserValueFormatterTest" -i
```
Expected: COMPILE FAIL.

- [ ] **Step 3: 구현**

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.view.UserValueView;
import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;

public class UserValueFormatter {

    public UserValueView format(String field, Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String raw = String.valueOf(rawValue);
        String display = switch (field) {
            case "age" -> "만 " + raw + "세";
            case "incomeMin", "incomeMax", "annualIncome" -> formatWon(raw);
            case "maritalStatus" -> safeEnumLabel(MaritalStatus.class, raw);
            case "education", "educationLevel" -> safeEnumLabel(Education.class, raw);
            case "employmentKind", "employmentStatus" -> safeEnumLabel(EmploymentKind.class, raw);
            case "majorField" -> safeEnumLabel(MajorField.class, raw);
            case "specializationField" -> safeEnumLabel(SpecializationField.class, raw);
            default -> raw;
        };
        return new UserValueView(raw, display);
    }

    private String formatWon(String raw) {
        try {
            long n = Long.parseLong(raw.trim());
            long manWon = n / 10_000;
            return String.format("%,d만원", manWon);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private <E extends Enum<E>> String safeEnumLabel(Class<E> enumType, String raw) {
        try {
            E value = Enum.valueOf(enumType, raw);
            return (String) value.getClass().getMethod("displayName").invoke(value);
        } catch (Exception e) {
            return raw;
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.UserValueFormatterTest" -i
```
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/UserValueFormatter.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/UserValueFormatterTest.java
git commit -m "feat(eligibility): UserValueFormatter 추가 — 사용자 값을 한국어 displayText로 변환"
```

---

## Task 5: VerdictTextGenerator (도메인 서비스) — TDD

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/service/VerdictTextGenerator.java`
- Test: `backend/src/test/java/com/youthfit/eligibility/domain/service/VerdictTextGeneratorTest.java`

평가 결과 + label + requirement + userValue → 자연어 한 줄.

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.UserValueView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerdictTextGenerator")
class VerdictTextGeneratorTest {

    private final VerdictTextGenerator generator = new VerdictTextGenerator();

    @Test
    @DisplayName("LIKELY_ELIGIBLE → \"{label} 조건을 충족해요\"")
    void eligible() {
        String text = generator.generate(
                EligibilityResult.LIKELY_ELIGIBLE,
                null,
                "연령",
                new RequirementView("BETWEEN", "만 19세 이상 34세 이하"),
                new UserValueView("29", "만 29세")
        );

        assertThat(text).isEqualTo("연령 조건을 충족해요");
    }

    @Test
    @DisplayName("LIKELY_INELIGIBLE → \"정책은 ___을 요구하는데, 내 정보는 ___이에요\"")
    void ineligible() {
        String text = generator.generate(
                EligibilityResult.LIKELY_INELIGIBLE,
                null,
                "고용 형태",
                new RequirementView("IN", "직장인, 자영업, 프리랜서, 일용직, 아르바이트"),
                new UserValueView("UNEMPLOYED", "미취업")
        );

        assertThat(text).isEqualTo(
                "정책은 직장인, 자영업, 프리랜서, 일용직, 아르바이트를 요구하는데, 내 정보는 미취업이에요"
        );
    }

    @Test
    @DisplayName("UNCERTAIN + MISSING_FIELD → \"{label} 정보가 없어요\"")
    void uncertainMissing() {
        String text = generator.generate(
                EligibilityResult.UNCERTAIN,
                UncertainReason.MISSING_FIELD,
                "가구 소득",
                new RequirementView("LTE", "5,000만원 이하"),
                null
        );

        assertThat(text).isEqualTo("가구 소득 정보가 없어요");
    }

    @Test
    @DisplayName("UNCERTAIN + AMBIGUOUS_SOURCE → \"정책 원문이 모호해 단정하기 어려워요\"")
    void uncertainAmbiguous() {
        String text = generator.generate(
                EligibilityResult.UNCERTAIN,
                UncertainReason.AMBIGUOUS_SOURCE,
                "학력",
                new RequirementView("EQ", "대학 졸업"),
                new UserValueView("COLLEGE_GRAD", "대학 졸업")
        );

        assertThat(text).isEqualTo("정책 원문이 모호해 단정하기 어려워요");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.VerdictTextGeneratorTest" -i
```
Expected: COMPILE FAIL.

- [ ] **Step 3: 구현**

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.UserValueView;

public class VerdictTextGenerator {

    public String generate(
            EligibilityResult result,
            UncertainReason uncertainReason,
            String label,
            RequirementView requirement,
            UserValueView userValue
    ) {
        return switch (result) {
            case LIKELY_ELIGIBLE -> label + " 조건을 충족해요";
            case LIKELY_INELIGIBLE -> "정책은 " + requirement.displayText()
                    + "를 요구하는데, 내 정보는 " + userValue.displayText() + "이에요";
            case UNCERTAIN -> uncertainReason == UncertainReason.AMBIGUOUS_SOURCE
                    ? "정책 원문이 모호해 단정하기 어려워요"
                    : label + " 정보가 없어요";
        };
    }
}
```

조사(을/를) 처리는 v1에서 `를` 로 단순화. 종성 받침 처리는 후속 개선.

테스트 케이스 `ineligible`은 `요구하는데` 앞에 `를`을 사용하지만, 실제로는 `직장인...아르바이트` 끝 글자가 `직`이라 `을`이 자연스러우나 v1에서는 단순화된 `를`을 사용합니다.

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.VerdictTextGeneratorTest" -i
```
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/VerdictTextGenerator.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/VerdictTextGeneratorTest.java
git commit -m "feat(eligibility): VerdictTextGenerator 추가 — 자연어 판단 한 줄 생성"
```

---

## Task 6: CriterionEvaluation 재설계 + EligibilityEvaluator 헬퍼 변경

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/domain/service/CriterionEvaluation.java`
- Modify: `backend/src/main/java/com/youthfit/eligibility/domain/service/EligibilityEvaluator.java`
- Modify: `backend/src/test/java/com/youthfit/eligibility/domain/service/CriterionEvaluationTest.java`

평가 결과는 raw 데이터(rule, userValue, uncertainReason)만 담고, 표시 문자열은 application 레이어에서 합성.

- [ ] **Step 1: CriterionEvaluation 재작성**

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.UncertainReason;

public record CriterionEvaluation(
        EligibilityRule rule,
        EligibilityResult result,
        Object userValue,
        UncertainReason uncertainReason
) {

    public String field() {
        return rule.getField();
    }

    public String label() {
        return rule.getLabel();
    }

    public static CriterionEvaluation eligible(EligibilityRule rule, Object userValue) {
        return new CriterionEvaluation(rule, EligibilityResult.LIKELY_ELIGIBLE, userValue, null);
    }

    public static CriterionEvaluation ineligible(EligibilityRule rule, Object userValue) {
        return new CriterionEvaluation(rule, EligibilityResult.LIKELY_INELIGIBLE, userValue, null);
    }

    public static CriterionEvaluation uncertain(EligibilityRule rule) {
        return new CriterionEvaluation(rule, EligibilityResult.UNCERTAIN, null, UncertainReason.MISSING_FIELD);
    }

    public static CriterionEvaluation lowConfidenceUncertain(EligibilityRule rule) {
        return new CriterionEvaluation(rule, EligibilityResult.UNCERTAIN, null, UncertainReason.AMBIGUOUS_SOURCE);
    }
}
```

`reason`/`sourceReference`/`confidenceNote` 필드는 모두 제거. 호출자(`EligibilityService`)가 `rule.getSourceReference()`로 직접 접근.

- [ ] **Step 2: EligibilityEvaluator 변경 없이 그대로 동작 확인**

`EligibilityEvaluator.java`의 헬퍼 호출(`CriterionEvaluation.eligible(rule, userValue)` 등)은 시그니처가 같아 변경 불필요.

- [ ] **Step 3: CriterionEvaluationTest 수정**

기존 `CriterionEvaluationTest.java`를 다음으로 교체:

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.UncertainReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CriterionEvaluation")
class CriterionEvaluationTest {

    private final EligibilityRule rule = EligibilityRule.builder()
            .policyId(1L).field("age").operator(RuleOperator.BETWEEN)
            .value("19~34").label("연령").sourceReference("자격 요건 > 연령 항목")
            .confidence(RuleConfidence.HIGH).build();

    @Test
    @DisplayName("eligible 헬퍼는 LIKELY_ELIGIBLE에 userValue를 보존한다")
    void eligibleHelper() {
        CriterionEvaluation eval = CriterionEvaluation.eligible(rule, 29);

        assertThat(eval.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        assertThat(eval.userValue()).isEqualTo(29);
        assertThat(eval.uncertainReason()).isNull();
        assertThat(eval.field()).isEqualTo("age");
        assertThat(eval.label()).isEqualTo("연령");
    }

    @Test
    @DisplayName("ineligible 헬퍼는 LIKELY_INELIGIBLE에 userValue를 보존한다")
    void ineligibleHelper() {
        CriterionEvaluation eval = CriterionEvaluation.ineligible(rule, 35);

        assertThat(eval.result()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        assertThat(eval.userValue()).isEqualTo(35);
        assertThat(eval.uncertainReason()).isNull();
    }

    @Test
    @DisplayName("uncertain 헬퍼는 MISSING_FIELD 사유를 갖는다")
    void uncertainHelper() {
        CriterionEvaluation eval = CriterionEvaluation.uncertain(rule);

        assertThat(eval.result()).isEqualTo(EligibilityResult.UNCERTAIN);
        assertThat(eval.userValue()).isNull();
        assertThat(eval.uncertainReason()).isEqualTo(UncertainReason.MISSING_FIELD);
    }

    @Test
    @DisplayName("lowConfidenceUncertain 헬퍼는 AMBIGUOUS_SOURCE 사유를 갖는다")
    void lowConfidenceUncertainHelper() {
        CriterionEvaluation eval = CriterionEvaluation.lowConfidenceUncertain(rule);

        assertThat(eval.result()).isEqualTo(EligibilityResult.UNCERTAIN);
        assertThat(eval.uncertainReason()).isEqualTo(UncertainReason.AMBIGUOUS_SOURCE);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.CriterionEvaluationTest" -i
```
Expected: 4 tests PASS.

- [ ] **Step 5: EligibilityEvaluatorTest 회귀 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.domain.service.EligibilityEvaluatorTest" -i
```
Expected: 모든 테스트 PASS (시그니처 변경 없음).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CriterionEvaluation.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CriterionEvaluationTest.java
git commit -m "refactor(eligibility): CriterionEvaluation을 raw 평가 데이터만 담도록 단순화"
```

---

## Task 7: SummaryHeadlineGenerator + GroupedCriteria — TDD

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/application/dto/result/GroupedCriteria.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/application/service/SummaryHeadlineGenerator.java`
- Test: `backend/src/test/java/com/youthfit/eligibility/application/service/SummaryHeadlineGeneratorTest.java`

`SummaryHeadlineGenerator`는 평가 결과 목록 → headline 한 줄 + 카운트.

- [ ] **Step 1: GroupedCriteria 생성 (CriterionResult import 후 사용)**

먼저 빈 placeholder. CriterionResult 재정의는 Task 8에서 진행하므로 일단 List<Object>로 두지 말고, 다음 Task에 포함시킵니다. **이 단계는 건너뜁니다.** GroupedCriteria는 Task 8에서 CriterionResult 재정의와 함께 작성합니다.

- [ ] **Step 2: 실패 테스트 작성**

```java
package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.SummaryView;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryHeadlineGenerator")
class SummaryHeadlineGeneratorTest {

    private final SummaryHeadlineGenerator generator = new SummaryHeadlineGenerator();

    @Test
    @DisplayName("미충족 1개 → \"{label} 1개 조건이 맞지 않아요\"")
    void singleIneligible() {
        SummaryView view = generator.generate(List.of(
                ineligibleEval("employmentKind", "고용 형태"),
                eligibleEval("age", "연령")
        ));

        assertThat(view.headline()).isEqualTo("고용 형태 1개 조건이 맞지 않아요");
        assertThat(view.eligibleCount()).isEqualTo(1);
        assertThat(view.uncertainCount()).isZero();
        assertThat(view.ineligibleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("미충족 2개 이상 → \"{대표 label} 등 N개 조건이 맞지 않아요\"")
    void multipleIneligible() {
        SummaryView view = generator.generate(List.of(
                ineligibleEval("age", "연령"),
                ineligibleEval("employmentKind", "고용 형태"),
                eligibleEval("region", "거주지")
        ));

        assertThat(view.headline()).isEqualTo("연령 등 2개 조건이 맞지 않아요");
        assertThat(view.ineligibleCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("미충족 0 + 미입력 N개 → \"{대표 label} 등 N개 정보가 더 필요해요\"")
    void onlyMissingFields() {
        SummaryView view = generator.generate(List.of(
                eligibleEval("age", "연령"),
                missingEval("annualIncome", "가구 소득"),
                missingEval("education", "학력")
        ));

        assertThat(view.headline()).isEqualTo("가구 소득 등 2개 정보가 더 필요해요");
        assertThat(view.uncertainCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("미충족 0 + 모호 N개 → \"정책 원문이 모호한 조건이 N개 있어요\"")
    void onlyAmbiguous() {
        SummaryView view = generator.generate(List.of(
                eligibleEval("age", "연령"),
                ambiguousEval("specializationField", "특화 분야")
        ));

        assertThat(view.headline()).isEqualTo("정책 원문이 모호한 조건이 1개 있어요");
    }

    @Test
    @DisplayName("모두 통과 → \"모든 조건을 충족해요\"")
    void allEligible() {
        SummaryView view = generator.generate(List.of(
                eligibleEval("age", "연령"),
                eligibleEval("region", "거주지")
        ));

        assertThat(view.headline()).isEqualTo("모든 조건을 충족해요");
        assertThat(view.eligibleCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("미입력 1개만 있을 때 → \"{label} 정보가 더 필요해요\" (등 N개 표현 없음)")
    void singleMissing() {
        SummaryView view = generator.generate(List.of(
                missingEval("annualIncome", "가구 소득")
        ));

        assertThat(view.headline()).isEqualTo("가구 소득 정보가 더 필요해요");
    }

    private CriterionEvaluation eligibleEval(String field, String label) {
        return CriterionEvaluation.eligible(makeRule(field, label), "value");
    }

    private CriterionEvaluation ineligibleEval(String field, String label) {
        return CriterionEvaluation.ineligible(makeRule(field, label), "value");
    }

    private CriterionEvaluation missingEval(String field, String label) {
        return CriterionEvaluation.uncertain(makeRule(field, label));
    }

    private CriterionEvaluation ambiguousEval(String field, String label) {
        return CriterionEvaluation.lowConfidenceUncertain(makeRule(field, label));
    }

    private EligibilityRule makeRule(String field, String label) {
        return EligibilityRule.builder()
                .policyId(1L).field(field).operator(RuleOperator.EQ)
                .value("v").label(label).confidence(RuleConfidence.HIGH).build();
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.application.service.SummaryHeadlineGeneratorTest" -i
```
Expected: COMPILE FAIL.

- [ ] **Step 4: 구현**

```java
package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.SummaryView;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;

import java.util.List;

public class SummaryHeadlineGenerator {

    public SummaryView generate(List<CriterionEvaluation> evaluations) {
        List<CriterionEvaluation> ineligible = evaluations.stream()
                .filter(e -> e.result() == EligibilityResult.LIKELY_INELIGIBLE)
                .toList();
        List<CriterionEvaluation> missing = evaluations.stream()
                .filter(e -> e.result() == EligibilityResult.UNCERTAIN
                        && e.uncertainReason() == UncertainReason.MISSING_FIELD)
                .toList();
        List<CriterionEvaluation> ambiguous = evaluations.stream()
                .filter(e -> e.result() == EligibilityResult.UNCERTAIN
                        && e.uncertainReason() == UncertainReason.AMBIGUOUS_SOURCE)
                .toList();
        long eligibleCount = evaluations.stream()
                .filter(e -> e.result() == EligibilityResult.LIKELY_ELIGIBLE)
                .count();
        long uncertainCount = missing.size() + ambiguous.size();

        String headline = buildHeadline(ineligible, missing, ambiguous);

        return new SummaryView(
                headline,
                (int) eligibleCount,
                (int) uncertainCount,
                ineligible.size()
        );
    }

    private String buildHeadline(
            List<CriterionEvaluation> ineligible,
            List<CriterionEvaluation> missing,
            List<CriterionEvaluation> ambiguous
    ) {
        if (ineligible.size() == 1) {
            return ineligible.get(0).label() + " 1개 조건이 맞지 않아요";
        }
        if (ineligible.size() > 1) {
            return ineligible.get(0).label() + " 등 " + ineligible.size() + "개 조건이 맞지 않아요";
        }
        if (missing.size() == 1) {
            return missing.get(0).label() + " 정보가 더 필요해요";
        }
        if (missing.size() > 1) {
            return missing.get(0).label() + " 등 " + missing.size() + "개 정보가 더 필요해요";
        }
        if (!ambiguous.isEmpty()) {
            return "정책 원문이 모호한 조건이 " + ambiguous.size() + "개 있어요";
        }
        return "모든 조건을 충족해요";
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.application.service.SummaryHeadlineGeneratorTest" -i
```
Expected: 6 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/service/SummaryHeadlineGenerator.java \
        backend/src/test/java/com/youthfit/eligibility/application/service/SummaryHeadlineGeneratorTest.java
git commit -m "feat(eligibility): SummaryHeadlineGenerator 추가 — 결과 요약 한 줄·카운트 생성"
```

---

## Task 8: CriterionResult / EligibilityJudgmentResult / GroupedCriteria 재설계

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/application/dto/result/CriterionResult.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/application/dto/result/GroupedCriteria.java`
- Modify: `backend/src/main/java/com/youthfit/eligibility/application/dto/result/EligibilityJudgmentResult.java`

- [ ] **Step 1: CriterionResult 교체**

```java
package com.youthfit.eligibility.application.dto.result;

import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.SourceView;
import com.youthfit.eligibility.domain.model.view.UserValueView;

public record CriterionResult(
        String field,
        String label,
        String result,
        UncertainReason uncertainReason,
        RequirementView requirement,
        UserValueView userValue,
        String verdictText,
        SourceView source
) {}
```

기존 `from(CriterionEvaluation)` static factory 삭제 — 합성에 4개 입력(evaluation + requirement + userValue + verdictText)이 필요하므로 service에서 직접 생성.

- [ ] **Step 2: GroupedCriteria 생성**

```java
package com.youthfit.eligibility.application.dto.result;

import java.util.List;

public record GroupedCriteria(
        List<CriterionResult> ineligible,
        List<CriterionResult> uncertain,
        List<CriterionResult> eligible
) {}
```

- [ ] **Step 3: EligibilityJudgmentResult 교체**

```java
package com.youthfit.eligibility.application.dto.result;

import com.youthfit.eligibility.domain.model.view.SummaryView;

public record EligibilityJudgmentResult(
        Long policyId,
        String policyTitle,
        String overallResult,
        SummaryView summary,
        GroupedCriteria criteria,
        String disclaimer
) {

    public static final String DISCLAIMER_TEXT =
            "본 결과는 참고용이며, 법적 효력이 있는 자격 판정이 아닙니다. 최종 확인은 공식 신청 채널에서 진행해 주세요.";
}
```

`overallResult` 타입을 `EligibilityResult`에서 `String`으로 변경 (response 직렬화와 일치, application도 같은 형태).

- [ ] **Step 4: 컴파일 확인 (다른 파일 깨질 것)**

Run:
```bash
cd backend && ./gradlew compileJava
```
Expected: COMPILE FAIL — `EligibilityService.java`, `EligibilityJudgmentResponse.java`, `CriterionResponse.java`가 깨짐. 다음 Task에서 수정.

- [ ] **Step 5: 일단 변경분만 commit (다음 task에서 후속 변경)**

이 task는 자체로 빌드 깨지므로 다음 Task와 묶어서 한 번에 커밋. **Step 5는 건너뛰고 Task 9로 진행.**

---

## Task 9: EligibilityService 재작성 (그룹핑 + 요약)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/application/service/EligibilityService.java`

평가 결과를 그룹핑하고, formatter들을 사용해 표시용 CriterionResult를 합성.

- [ ] **Step 1: EligibilityService 전체 교체**

```java
package com.youthfit.eligibility.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.CriterionResult;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.dto.result.GroupedCriteria;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.SourceView;
import com.youthfit.eligibility.domain.model.view.SummaryView;
import com.youthfit.eligibility.domain.model.view.UserValueView;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;
import com.youthfit.eligibility.domain.service.EligibilityEvaluator;
import com.youthfit.eligibility.domain.service.RequirementFormatter;
import com.youthfit.eligibility.domain.service.UserValueFormatter;
import com.youthfit.eligibility.domain.service.VerdictTextGenerator;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EligibilityService {

    private final EligibilityRuleRepository eligibilityRuleRepository;
    private final EligibilityProfileRepository eligibilityProfileRepository;
    private final PolicyRepository policyRepository;

    private final EligibilityEvaluator evaluator = new EligibilityEvaluator();
    private final RequirementFormatter requirementFormatter = new RequirementFormatter();
    private final UserValueFormatter userValueFormatter = new UserValueFormatter();
    private final VerdictTextGenerator verdictGenerator = new VerdictTextGenerator();
    private final SummaryHeadlineGenerator summaryGenerator = new SummaryHeadlineGenerator();

    @Transactional(readOnly = true)
    public EligibilityJudgmentResult judgeEligibility(Long userId, JudgeEligibilityCommand command) {
        EligibilityProfile profile = eligibilityProfileRepository.findByUserId(userId)
                .orElseGet(() -> EligibilityProfile.empty(userId));

        Policy policy = policyRepository.findById(command.policyId())
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다"));

        List<EligibilityRule> rules = eligibilityRuleRepository.findAllByPolicyId(command.policyId());

        List<CriterionEvaluation> evaluations = rules.stream()
                .map(rule -> evaluator.evaluateRule(rule, profile))
                .toList();

        List<CriterionResult> results = evaluations.stream()
                .map(this::toCriterionResult)
                .toList();

        GroupedCriteria grouped = groupByResult(results);
        SummaryView summary = summaryGenerator.generate(evaluations);
        EligibilityResult overall = determineOverall(evaluations);

        return new EligibilityJudgmentResult(
                policy.getId(),
                policy.getTitle(),
                overall.name(),
                summary,
                grouped,
                EligibilityJudgmentResult.DISCLAIMER_TEXT
        );
    }

    private CriterionResult toCriterionResult(CriterionEvaluation eval) {
        EligibilityRule rule = eval.rule();
        RequirementView requirement = requirementFormatter.format(
                rule.getField(), rule.getOperator(), rule.getValue()
        );
        UserValueView userValue = userValueFormatter.format(rule.getField(), eval.userValue());
        String verdictText = verdictGenerator.generate(
                eval.result(), eval.uncertainReason(), rule.getLabel(), requirement, userValue
        );
        SourceView source = new SourceView(rule.getSourceReference());
        return new CriterionResult(
                rule.getField(),
                rule.getLabel(),
                eval.result().name(),
                eval.uncertainReason(),
                requirement,
                userValue,
                verdictText,
                source
        );
    }

    private GroupedCriteria groupByResult(List<CriterionResult> results) {
        return new GroupedCriteria(
                results.stream().filter(r -> "LIKELY_INELIGIBLE".equals(r.result())).toList(),
                results.stream().filter(r -> "UNCERTAIN".equals(r.result())).toList(),
                results.stream().filter(r -> "LIKELY_ELIGIBLE".equals(r.result())).toList()
        );
    }

    private EligibilityResult determineOverall(List<CriterionEvaluation> evaluations) {
        if (evaluations.stream().anyMatch(e -> e.result() == EligibilityResult.LIKELY_INELIGIBLE)) {
            return EligibilityResult.LIKELY_INELIGIBLE;
        }
        if (evaluations.stream().anyMatch(e -> e.result() == EligibilityResult.UNCERTAIN)) {
            return EligibilityResult.UNCERTAIN;
        }
        return EligibilityResult.LIKELY_ELIGIBLE;
    }
}
```

- [ ] **Step 2: 빌드 확인 (presentation DTO 깨짐 — 다음 task에서 수정)**

Run:
```bash
cd backend && ./gradlew compileJava
```
Expected: `EligibilityJudgmentResponse.java`, `CriterionResponse.java`만 깨짐.

---

## Task 10: presentation DTO 재작성 (CriterionResponse / EligibilityJudgmentResponse)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/presentation/dto/response/CriterionResponse.java`
- Modify: `backend/src/main/java/com/youthfit/eligibility/presentation/dto/response/EligibilityJudgmentResponse.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/presentation/dto/response/GroupedCriteriaResponse.java`

application result와 1:1로 직렬화하는 껍데기 record.

- [ ] **Step 1: CriterionResponse 교체**

```java
package com.youthfit.eligibility.presentation.dto.response;

import com.youthfit.eligibility.application.dto.result.CriterionResult;
import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.SourceView;
import com.youthfit.eligibility.domain.model.view.UserValueView;

public record CriterionResponse(
        String field,
        String label,
        String result,
        String uncertainReason,
        RequirementView requirement,
        UserValueView userValue,
        String verdictText,
        SourceView source
) {

    public static CriterionResponse from(CriterionResult r) {
        return new CriterionResponse(
                r.field(),
                r.label(),
                r.result(),
                r.uncertainReason() == null ? null : r.uncertainReason().name(),
                r.requirement(),
                r.userValue(),
                r.verdictText(),
                r.source()
        );
    }
}
```

- [ ] **Step 2: GroupedCriteriaResponse 생성**

```java
package com.youthfit.eligibility.presentation.dto.response;

import com.youthfit.eligibility.application.dto.result.GroupedCriteria;

import java.util.List;

public record GroupedCriteriaResponse(
        List<CriterionResponse> ineligible,
        List<CriterionResponse> uncertain,
        List<CriterionResponse> eligible
) {

    public static GroupedCriteriaResponse from(GroupedCriteria g) {
        return new GroupedCriteriaResponse(
                g.ineligible().stream().map(CriterionResponse::from).toList(),
                g.uncertain().stream().map(CriterionResponse::from).toList(),
                g.eligible().stream().map(CriterionResponse::from).toList()
        );
    }
}
```

- [ ] **Step 3: EligibilityJudgmentResponse 교체**

```java
package com.youthfit.eligibility.presentation.dto.response;

import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.domain.model.view.SummaryView;

public record EligibilityJudgmentResponse(
        Long policyId,
        String policyTitle,
        String overallResult,
        SummaryView summary,
        GroupedCriteriaResponse criteria,
        String disclaimer
) {

    public static EligibilityJudgmentResponse from(EligibilityJudgmentResult result) {
        return new EligibilityJudgmentResponse(
                result.policyId(),
                result.policyTitle(),
                result.overallResult(),
                result.summary(),
                GroupedCriteriaResponse.from(result.criteria()),
                result.disclaimer()
        );
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run:
```bash
cd backend && ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

---

## Task 11: EligibilityServiceTest 재작성 + 회귀 테스트

**Files:**
- Modify: `backend/src/test/java/com/youthfit/eligibility/application/service/EligibilityServiceTest.java`

기존 테스트는 `result.criteria()`가 `List<CriterionResult>`였고 `result.missingFields()` 가 있었지만, 새 구조는 `result.criteria()`가 `GroupedCriteria`이고 `missingFields`는 제거됨.

- [ ] **Step 1: 기존 EligibilityServiceTest 검사 후 수정**

먼저 현재 테스트 전체를 읽어 시그니처 변경에 따라 깨지는 지점을 파악.

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.application.service.EligibilityServiceTest" -i
```
Expected: COMPILE FAIL (criteria, missingFields, overallResult 타입 깨짐).

- [ ] **Step 2: 기존 테스트의 어설션을 새 구조에 맞게 갱신**

검사 패턴 변경:
- `result.overallResult()` → `String` (`.isEqualTo("LIKELY_ELIGIBLE")` 형태로)
- `result.criteria()` → `GroupedCriteria` 객체
- `result.criteria().eligible()`, `.ineligible()`, `.uncertain()` 으로 접근
- `result.missingFields()` 호출 모두 제거 → `result.criteria().uncertain()` 의 항목들이 곧 미입력 또는 모호 사유. 미입력만 보고 싶으면 `uncertainReason() == "MISSING_FIELD"` 필터링.
- 기존 테스트 어설션들을 다음 패턴으로 바꾼다:
  - `assertThat(result.criteria()).hasSize(2)` → `assertThat(result.criteria().eligible()).hasSize(2)` (충족 2개) / `.ineligible()` / `.uncertain()` 합산
  - `assertThat(result.missingFields()).isEmpty()` → `assertThat(result.criteria().uncertain()).isEmpty()`
- 새로 추가할 어설션:
  - `assertThat(result.summary()).isNotNull()` 및 `assertThat(result.summary().headline()).isNotBlank()`
  - 적절한 시나리오에서 `result.criteria().eligible().get(0).requirement().displayText()` 가 빈 문자열이 아님

기존 시나리오(allCriteriaMet, oneCriterionFails 등)는 모두 유지하되 어설션만 새 구조로 옮긴다. 한 번에 모두 통과시키지 말고 한 시나리오씩 빌드·테스트·통과 리듬으로.

- [ ] **Step 3: 추가 시나리오 — UNCERTAIN 사유 분리 검증**

`@Nested class JudgeEligibility` 안에 새 테스트 추가:

```java
        @Test
        @DisplayName("MISSING_FIELD UNCERTAIN과 AMBIGUOUS_SOURCE UNCERTAIN을 구분해 응답에 노출한다")
        void uncertainReasonsSeparated() {
            EligibilityProfile profile = createMockProfile(29, null, null);
            Policy policy = createMockPolicy();
            EligibilityRule lowConfidenceRule = EligibilityRule.builder()
                    .policyId(1L).field("specializationField").operator(RuleOperator.EQ)
                    .value("WOMAN").label("특화 분야")
                    .confidence(com.youthfit.eligibility.domain.model.RuleConfidence.LOW).build();
            ReflectionTestUtils.setField(lowConfidenceRule, "id", 99L);
            List<EligibilityRule> rules = List.of(
                    createRule("annualIncome", RuleOperator.LTE, "50000000", "가구 소득"),
                    lowConfidenceRule
            );
            setupMocks(profile, policy, rules);

            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(
                    1L, new JudgeEligibilityCommand(1L)
            );

            assertThat(result.overallResult()).isEqualTo("UNCERTAIN");
            assertThat(result.criteria().uncertain()).hasSize(2);
            assertThat(result.criteria().uncertain())
                    .extracting(c -> c.uncertainReason() == null ? null : c.uncertainReason().name())
                    .containsExactlyInAnyOrder("MISSING_FIELD", "AMBIGUOUS_SOURCE");
        }

        @Test
        @DisplayName("응답의 CriterionResult는 requirement·userValue·verdictText·source를 채워서 돌려준다")
        void responseShapeIsHumanFriendly() {
            EligibilityProfile profile = createMockProfile(29, "1100000000", 30000000L);
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령")
            );
            setupMocks(profile, policy, rules);

            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(
                    1L, new JudgeEligibilityCommand(1L)
            );

            assertThat(result.criteria().eligible()).hasSize(1);
            var c = result.criteria().eligible().get(0);
            assertThat(c.requirement().displayText()).isEqualTo("만 19세 이상 34세 이하");
            assertThat(c.userValue().displayText()).isEqualTo("만 29세");
            assertThat(c.verdictText()).isEqualTo("연령 조건을 충족해요");
            assertThat(c.source()).isNotNull();
        }
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.application.service.EligibilityServiceTest" -i
```
Expected: 모든 테스트 PASS.

- [ ] **Step 5: presentation 컨트롤러 통합 테스트 회귀 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.eligibility.presentation.controller.EligibilityControllerTest" \
  --tests "com.youthfit.eligibility.presentation.controller.EligibilityControllerIntegrationTest" -i
```
Expected: 응답 JSON 스키마 변경 영향이 있을 경우 FAIL.

만약 통합 테스트가 옛 응답 스키마(`reason`, `sourceReference` 필드)를 검증하면 그 어설션을 새 구조(`requirement.displayText`, `verdictText`, `source.snippet`, `criteria.eligible[]`, `summary.headline` 등) 로 갱신.

- [ ] **Step 6: 전체 백엔드 테스트 실행**

Run:
```bash
cd backend && ./gradlew test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit (Task 8 + 9 + 10 + 11 한 묶음)**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/dto/result/CriterionResult.java \
        backend/src/main/java/com/youthfit/eligibility/application/dto/result/GroupedCriteria.java \
        backend/src/main/java/com/youthfit/eligibility/application/dto/result/EligibilityJudgmentResult.java \
        backend/src/main/java/com/youthfit/eligibility/application/service/EligibilityService.java \
        backend/src/main/java/com/youthfit/eligibility/presentation/dto/response/CriterionResponse.java \
        backend/src/main/java/com/youthfit/eligibility/presentation/dto/response/GroupedCriteriaResponse.java \
        backend/src/main/java/com/youthfit/eligibility/presentation/dto/response/EligibilityJudgmentResponse.java \
        backend/src/test/java/com/youthfit/eligibility/application/service/EligibilityServiceTest.java \
        backend/src/test/java/com/youthfit/eligibility/presentation/controller/
git commit -m "refactor(eligibility): 응답 DTO를 사용자 친화 구조로 재설계 — 그룹핑·요약·요구·내값 분리"
```

---

## Task 12: 프론트엔드 타입 갱신

**Files:**
- Modify: `frontend/src/types/policy.ts` (lines 129~146 부근의 `CriterionItem`, `EligibilityResponse`)

- [ ] **Step 1: 기존 인터페이스 교체**

`frontend/src/types/policy.ts:129~146`을 다음으로 교체:

```typescript
/* ── Eligibility ── */

export type UncertainReason = 'MISSING_FIELD' | 'AMBIGUOUS_SOURCE' | null;

export interface RequirementView {
  operator: string;
  displayText: string;
}

export interface UserValueView {
  raw: string;
  displayText: string;
}

export interface SourceView {
  snippet: string | null;
}

export interface CriterionItem {
  field: string;
  label: string;
  result: EligibilityResult;
  uncertainReason: UncertainReason;
  requirement: RequirementView;
  userValue: UserValueView | null;
  verdictText: string;
  source: SourceView;
}

export interface GroupedCriteria {
  ineligible: CriterionItem[];
  uncertain: CriterionItem[];
  eligible: CriterionItem[];
}

export interface SummaryView {
  headline: string;
  eligibleCount: number;
  uncertainCount: number;
  ineligibleCount: number;
}

export interface EligibilityResponse {
  policyId: number;
  policyTitle: string;
  overallResult: EligibilityResult;
  summary: SummaryView;
  criteria: GroupedCriteria;
  disclaimer: string;
}
```

- [ ] **Step 2: 타입 체크**

Run:
```bash
cd frontend && npm run build
```
Expected: `PolicyDetailPage.tsx` 에서 옛 필드(`reason`, `missingFields`, `criteria.map`) 참조 때문에 ERROR.

다음 Task에서 수정.

---

## Task 13: 프론트엔드 신규 컴포넌트 생성 — eligibilityStyles + EligibilityHeader

**Files:**
- Create: `frontend/src/components/policy/eligibility/eligibilityStyles.ts`
- Create: `frontend/src/components/policy/eligibility/EligibilityHeader.tsx`

- [ ] **Step 1: 디렉토리 생성 (Write가 자동 처리)**
- [ ] **Step 2: eligibilityStyles.ts 작성**

```typescript
import { CheckCircle, AlertCircle, XCircle } from 'lucide-react';
import type { EligibilityResult } from '@/types/policy';

export type ResultVariant = EligibilityResult;

export const RESULT_CONFIG: Record<
  ResultVariant,
  {
    icon: typeof CheckCircle;
    color: string;
    bg: string;
    label: string;
    groupLabel: string;
  }
> = {
  LIKELY_ELIGIBLE: {
    icon: CheckCircle,
    color: 'text-success-500',
    bg: 'bg-success-50',
    label: '해당 가능성 높음',
    groupLabel: '충족한 조건',
  },
  UNCERTAIN: {
    icon: AlertCircle,
    color: 'text-warning-500',
    bg: 'bg-warning-50',
    label: '추가 확인 필요',
    groupLabel: '추가 확인이 필요한 조건',
  },
  LIKELY_INELIGIBLE: {
    icon: XCircle,
    color: 'text-error-500',
    bg: 'bg-error-50',
    label: '해당 가능성 낮음',
    groupLabel: '충족하지 못한 조건',
  },
};
```

- [ ] **Step 3: EligibilityHeader.tsx 작성**

```typescript
import { cn } from '@/lib/cn';
import type { EligibilityResult, SummaryView } from '@/types/policy';
import { RESULT_CONFIG } from './eligibilityStyles';

interface Props {
  overallResult: EligibilityResult;
  summary: SummaryView;
}

export default function EligibilityHeader({ overallResult, summary }: Props) {
  const cfg = RESULT_CONFIG[overallResult];
  const Icon = cfg.icon;
  return (
    <div className={cn('mb-5 rounded-xl px-4 py-4 text-center', cfg.bg)}>
      <div className="mb-1 flex items-center justify-center gap-2">
        <Icon className={cn('h-5 w-5', cfg.color)} />
        <p className={cn('text-lg font-bold', cfg.color)}>{cfg.label}</p>
      </div>
      <p className="text-sm text-neutral-700">{summary.headline}</p>
    </div>
  );
}
```

- [ ] **Step 4: 빌드 확인**

Run:
```bash
cd frontend && npm run build
```
Expected: 신규 파일 OK. 옛 인라인 카드는 여전히 깨짐.

---

## Task 14: CriterionRow + CriterionGroup 작성

**Files:**
- Create: `frontend/src/components/policy/eligibility/CriterionRow.tsx`
- Create: `frontend/src/components/policy/eligibility/CriterionGroup.tsx`
- Create: `frontend/src/components/policy/eligibility/fieldToRowKey.ts`

- [ ] **Step 1: fieldToRowKey.ts 작성**

EligibilityInfoCard의 Row key와 적합도 룰 field명을 매핑.

```typescript
const FIELD_TO_ROW_KEY: Record<string, string> = {
  age: 'age',
  region: 'region',
  legalDongCode: 'region',
  maritalStatus: 'marital',
  education: 'education',
  educationLevel: 'education',
  incomeMin: 'income',
  incomeMax: 'income',
  annualIncome: 'income',
  employmentKind: 'employment',
  employmentStatus: 'employment',
  majorField: 'major',
  specializationField: 'specialization',
};

export function fieldToRowKey(field: string): string | null {
  return FIELD_TO_ROW_KEY[field] ?? null;
}
```

- [ ] **Step 2: CriterionRow.tsx 작성**

```typescript
import { Link } from 'react-router-dom';
import { Quote } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { CriterionItem } from '@/types/policy';
import { fieldToRowKey } from './fieldToRowKey';

interface Props {
  item: CriterionItem;
}

export default function CriterionRow({ item }: Props) {
  const isMissing = item.uncertainReason === 'MISSING_FIELD';
  const isAmbiguous = item.uncertainReason === 'AMBIGUOUS_SOURCE';
  const rowKey = fieldToRowKey(item.field);

  return (
    <li className="rounded-xl border border-neutral-100 bg-white p-4">
      {/* Row header */}
      <div className="mb-3 flex items-center gap-2">
        <p className="text-sm font-semibold text-neutral-900">{item.label}</p>
        {isAmbiguous && (
          <span className="inline-flex items-center rounded bg-warning-50 px-1.5 py-0.5 text-[10px] font-medium text-warning-700">
            근거 모호
          </span>
        )}
      </div>

      {/* Requirement vs userValue grid */}
      <dl className="mb-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
        <dt className="text-neutral-500">정책 요구</dt>
        <dd className="text-neutral-900">{item.requirement.displayText}</dd>
        <dt className="text-neutral-500">내 정보</dt>
        <dd className={cn('break-words', item.userValue ? 'text-neutral-900' : 'italic text-neutral-400')}>
          {item.userValue ? item.userValue.displayText : '미입력'}
        </dd>
      </dl>

      {/* Verdict text */}
      <p className="mb-3 text-sm leading-relaxed text-neutral-700">{item.verdictText}</p>

      {/* MISSING_FIELD CTA */}
      {isMissing && rowKey && (
        <Link
          to={`/mypage?focus=${encodeURIComponent(rowKey)}`}
          className="mb-3 inline-flex items-center gap-1 text-xs font-medium text-brand-800 hover:underline"
        >
          👉 정보 입력하면 더 정확해져요
        </Link>
      )}

      {/* Source snippet */}
      {item.source?.snippet && (
        <div className="flex items-start gap-1.5 rounded bg-neutral-50 p-2 text-xs italic text-neutral-600">
          <Quote className="mt-0.5 h-3 w-3 shrink-0 text-neutral-400" />
          <span>{item.source.snippet}</span>
        </div>
      )}
    </li>
  );
}
```

- [ ] **Step 3: CriterionGroup.tsx 작성**

```typescript
import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { CriterionItem, EligibilityResult } from '@/types/policy';
import { RESULT_CONFIG } from './eligibilityStyles';
import CriterionRow from './CriterionRow';

interface Props {
  variant: EligibilityResult;
  items: CriterionItem[];
  defaultCollapsed?: boolean;
}

export default function CriterionGroup({ variant, items, defaultCollapsed = false }: Props) {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  if (items.length === 0) return null;

  const cfg = RESULT_CONFIG[variant];
  const Icon = cfg.icon;

  return (
    <div className="mb-5">
      <button
        type="button"
        onClick={() => setCollapsed((v) => !v)}
        className="mb-2 flex w-full items-center justify-between text-left"
        aria-expanded={!collapsed}
      >
        <div className="flex items-center gap-2">
          <Icon className={cn('h-4 w-4', cfg.color)} />
          <span className="text-sm font-semibold text-neutral-800">
            {cfg.groupLabel} · {items.length}
          </span>
        </div>
        <ChevronDown
          className={cn(
            'h-4 w-4 text-neutral-400 transition-transform',
            collapsed ? '' : 'rotate-180',
          )}
        />
      </button>
      {!collapsed && (
        <ul className="space-y-2">
          {items.map((item, idx) => (
            <CriterionRow key={`${item.field}-${idx}`} item={item} />
          ))}
        </ul>
      )}
    </div>
  );
}
```

- [ ] **Step 4: 빌드 확인**

Run:
```bash
cd frontend && npm run build
```
Expected: 신규 파일 OK. 옛 인라인 카드는 여전히 깨짐.

---

## Task 15: EligibilityCard + EligibilityFooter + PolicyDetailPage 교체

**Files:**
- Create: `frontend/src/components/policy/eligibility/EligibilityFooter.tsx`
- Create: `frontend/src/components/policy/eligibility/EligibilityCard.tsx`
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: EligibilityFooter.tsx 작성**

```typescript
import { ExternalLink } from 'lucide-react';

interface Props {
  disclaimer: string;
  sourceUrl: string | null;
}

export default function EligibilityFooter({ disclaimer, sourceUrl }: Props) {
  return (
    <div className="border-t border-neutral-100 pt-4">
      <p className="text-xs leading-relaxed text-neutral-500">{disclaimer}</p>
      {sourceUrl && (
        <a
          href={sourceUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-3 flex items-center justify-center gap-1.5 rounded-xl border border-brand-800 px-4 py-2.5 text-sm font-semibold text-brand-800 transition-colors hover:bg-brand-100"
        >
          공식 신청 채널에서 확인
          <ExternalLink className="h-3.5 w-3.5" />
        </a>
      )}
    </div>
  );
}
```

- [ ] **Step 2: EligibilityCard.tsx 작성**

```typescript
import { Loader2 } from 'lucide-react';
import type { EligibilityResponse } from '@/types/policy';
import EligibilityHeader from './EligibilityHeader';
import CriterionGroup from './CriterionGroup';
import EligibilityFooter from './EligibilityFooter';

interface Props {
  isAuthenticated: boolean;
  eligibility: EligibilityResponse | null;
  loading: boolean;
  onCheck: () => void;
  onLoginPrompt: () => void;
  sourceUrl: string | null;
}

export default function EligibilityCard({
  isAuthenticated,
  eligibility,
  loading,
  onCheck,
  onLoginPrompt,
  sourceUrl,
}: Props) {
  return (
    <section className="rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-4 text-xl font-semibold text-neutral-900">내 적합도 확인</h2>

      {!isAuthenticated && (
        <button
          onClick={onLoginPrompt}
          className="flex h-11 w-full items-center justify-center rounded-xl bg-brand-800 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          내 적합도 확인하기
        </button>
      )}

      {isAuthenticated && !eligibility && !loading && (
        <button
          onClick={onCheck}
          className="flex h-11 w-full items-center justify-center rounded-xl bg-brand-800 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          내 적합도 확인하기
        </button>
      )}

      {isAuthenticated && loading && (
        <div className="flex flex-col items-center gap-3 py-6">
          <Loader2 className="h-8 w-8 animate-spin text-brand-800" />
          <p className="text-sm text-neutral-500">적합도를 분석하고 있어요...</p>
        </div>
      )}

      {isAuthenticated && eligibility && !loading && (
        <div>
          <EligibilityHeader
            overallResult={eligibility.overallResult}
            summary={eligibility.summary}
          />

          <CriterionGroup
            variant="LIKELY_INELIGIBLE"
            items={eligibility.criteria.ineligible}
          />
          <CriterionGroup
            variant="UNCERTAIN"
            items={eligibility.criteria.uncertain}
          />
          <CriterionGroup
            variant="LIKELY_ELIGIBLE"
            items={eligibility.criteria.eligible}
            defaultCollapsed={true}
          />

          <EligibilityFooter
            disclaimer={eligibility.disclaimer}
            sourceUrl={sourceUrl}
          />
        </div>
      )}
    </section>
  );
}
```

- [ ] **Step 3: PolicyDetailPage.tsx 수정 — 인라인 카드 제거 + import**

`frontend/src/pages/PolicyDetailPage.tsx`:

1. 상단 import 부분에서 옛 항목 정리:
   - 제거: `import type { CriterionItem, EligibilityResult }` (사용하지 않게 됨)
   - 제거: `CheckCircle, AlertCircle, XCircle, Loader2, ExternalLink` 중 PolicyDetailPage가 더 이상 사용하지 않는 것들 (`Loader2`, `ExternalLink`, `CheckCircle`, `AlertCircle`, `XCircle`은 EligibilityCard로 이동했으나 다른 곳에서 쓰이는지 확인 후 정리)
   - 추가: `import EligibilityCard from '@/components/policy/eligibility/EligibilityCard';`
2. `RESULT_CONFIG` / `OVERALL_COLOR` 상수 제거 (eligibilityStyles로 이동).
3. 287~394행의 `function EligibilityCard(...)` 인라인 정의 통째로 제거.
4. `<EligibilityCard ... />` 호출부는 그대로 유지 (props 시그니처가 같음).

수정 후 PolicyDetailPage.tsx의 적합도 관련 부분이 깔끔하게 정리되었는지 확인.

- [ ] **Step 4: 빌드 + 타입 체크**

Run:
```bash
cd frontend && npm run build
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/policy.ts \
        frontend/src/components/policy/eligibility/ \
        frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(frontend): 적합도 카드 사용자 친화 컴포넌트 분리·재구성"
```

---

## Task 16: MyPage `?focus=` 라우팅 + EligibilityInfoCard 자동 오픈

**Files:**
- Modify: `frontend/src/pages/MyPage.tsx`
- Modify: `frontend/src/components/personal-info/EligibilityInfoCard.tsx`

PolicyDetailPage의 MISSING_FIELD CTA가 `/mypage?focus={rowKey}` 로 라우팅하면, MyPage가 EligibilityInfoCard로 스크롤하고, 카드가 해당 row를 자동으로 펼친다.

- [ ] **Step 1: EligibilityInfoCard에 initialOpen prop + sectionRef 추가**

`frontend/src/components/personal-info/EligibilityInfoCard.tsx`의 변경:

1. RowKey 타입을 export
   ```typescript
   export type RowKey =
     | 'region'
     | 'age'
     | 'marital'
     | 'education'
     | 'income'
     | 'employment'
     | 'major'
     | 'specialization';
   ```
2. `EligibilityInfoCardProps`에 `initialOpen?: RowKey | null` 추가:
   ```typescript
   interface EligibilityInfoCardProps {
     profile: EligibilityProfile;
     onUpdate: (data: UpdateEligibilityProfileRequest) => void;
     isUpdating?: boolean;
     initialOpen?: RowKey | null;
   }
   ```
3. 컴포넌트 내부에서 `initialOpen`이 들어오면 `open` 상태를 그것으로 초기화:
   ```typescript
   const [open, setOpen] = useState<RowKey | null>(initialOpen ?? null);

   useEffect(() => {
     if (initialOpen) setOpen(initialOpen);
   }, [initialOpen]);
   ```
4. 최상단 `<section>`에 `id="eligibility-info-card"` 부여 (스크롤 타겟).

- [ ] **Step 2: MyPage에 useSearchParams 처리 추가**

`frontend/src/pages/MyPage.tsx`:

1. import 추가:
   ```typescript
   import { useSearchParams } from 'react-router-dom';
   import type { RowKey } from '@/components/personal-info/EligibilityInfoCard';
   ```
2. 컴포넌트 내부에서:
   ```typescript
   const [searchParams] = useSearchParams();
   const focusKey = (searchParams.get('focus') as RowKey | null) ?? null;

   useEffect(() => {
     if (!focusKey) return;
     const el = document.getElementById('eligibility-info-card');
     if (el) {
       el.scrollIntoView({ behavior: 'smooth', block: 'start' });
     }
   }, [focusKey]);
   ```
3. `<EligibilityInfoCard ... initialOpen={focusKey} />` 전달.

이미 `useEffect`/`useSearchParams` 가 import 되어 있는지 확인 후 중복 회피.

- [ ] **Step 3: 빌드 + 타입 체크**

Run:
```bash
cd frontend && npm run build
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/MyPage.tsx \
        frontend/src/components/personal-info/EligibilityInfoCard.tsx
git commit -m "feat(frontend): MyPage ?focus= 쿼리로 적합도 정보 row 자동 오픈"
```

---

## Task 17: 수동 스모크 테스트

**Files:** (없음 — 실행만)

- [ ] **Step 1: 백엔드 / 프론트엔드 동시 기동**

두 개의 터미널:
```bash
# Terminal A
cd backend && ./gradlew bootRun
```
```bash
# Terminal B
cd frontend && npm run dev
```

- [ ] **Step 2: 적합도 판정 시나리오 검증 — 충족 케이스**

브라우저로 `http://localhost:5173/policies/{충족 가능 정책 ID}` 접속 (예: 시드된 정책 1번). 로그인 후 "내 적합도 확인하기" 클릭.

확인 사항:
- 헤더에 "해당 가능성 높음" + summary.headline 노출
- "충족한 조건 · N" 그룹이 기본 접힘 상태로 표시
- 펼치면 각 룰에 "정책 요구 / 내 정보" 그리드 + "verdictText" + 인용구가 모두 보임
- 옛 raw enum 노출 없음 (`UNEMPLOYED`, `EMPLOYEE,SELF_EMPLOYED,...` 등 영문 노출 없음)

- [ ] **Step 3: 적합도 판정 시나리오 — 미충족 케이스**

프로필을 `UNEMPLOYED` 등 미충족 값으로 바꾼 뒤 재판정.

확인 사항:
- 헤더에 "해당 가능성 낮음" + 요약 한 줄
- "충족하지 못한 조건" 그룹이 맨 위에 펼쳐져 있음
- "정책 요구: 직장인, 자영업, ..." / "내 정보: 미취업" 표시
- "정책은 ... 를 요구하는데, 내 정보는 미취업이에요" verdictText

- [ ] **Step 4: UNCERTAIN MISSING_FIELD 시나리오**

프로필에서 한 항목을 비운 뒤 (예: 가구 소득 비움) 적합도 재판정.

확인 사항:
- "추가 확인이 필요한 조건" 그룹에 해당 룰 표시
- "내 정보: 미입력" (이탤릭, 회색)
- "👉 정보 입력하면 더 정확해져요" 링크 존재
- 링크 클릭 시 `/mypage?focus={rowKey}` 로 이동, MyPage 도달 후 적합도 정보 카드로 스크롤되며 해당 row가 자동으로 펼쳐짐

- [ ] **Step 5: UNCERTAIN AMBIGUOUS_SOURCE 시나리오 (가능하면)**

DB에 `confidence='LOW'` 룰이 있는 정책으로 이동.

확인 사항:
- "근거 모호" 배지가 라벨 옆에 표시
- "정책 원문이 모호해 단정하기 어려워요" verdictText
- CTA 링크는 표시되지 않음

- [ ] **Step 6: (있다면) 콘솔 / 네트워크 에러 확인**

브라우저 DevTools에서 응답 JSON 구조와 콘솔 에러 부재를 확인.

- [ ] **Step 7: 결과 메모**

스모크 테스트 결과를 메모로 정리. 깨진 부분이 있으면 별도 task로 추가하여 수정.

---

## Task 18: 전체 회귀 + 최종 commit + PR 준비

**Files:** (없음 — 실행만)

- [ ] **Step 1: 백엔드 전체 테스트**

Run:
```bash
cd backend && ./gradlew test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 프론트엔드 빌드 + 테스트**

Run:
```bash
cd frontend && npm run build && npm run test --if-present
```
Expected: BUILD SUCCESS, 모든 테스트 PASS.

- [ ] **Step 3: 변경 요약 확인**

Run:
```bash
git log --oneline main..HEAD
```
Expected: 깔끔한 commit 히스토리 (Task 단위로 7~8개 커밋).

- [ ] **Step 4: 최종 PR 작성 안내**

이 plan의 마지막 단계로 PR을 만들 준비를 한다. PR 제목과 본문은 `superpowers:finishing-a-development-branch` 또는 사용자 지정 `create-pr` 스킬로 작성.

PR 제목 예시:
```
feat(eligibility): 정책 상세 적합도 카드 사용자 친화 개편
```

PR 본문에는 다음을 포함:
- 변경 요지 (raw enum → 한국어, 결과별 그룹핑, 요구 vs 내 값, 신뢰도 배지, MISSING CTA)
- spec 링크
- 스크린샷 (선택)

---

## 자기 검토

### Spec coverage 확인
- 4.1 도메인 enum displayName → Task 1 ✓
- 4.2 RequirementFormatter → Task 3 ✓
- 4.3 UserValueFormatter → Task 4 ✓
- 4.4 SummaryHeadlineGenerator → Task 7 ✓
- 4.5 CriterionEvaluation 변경 → Task 6 ✓
- 4.6 application/dto/result 재설계 → Task 8 ✓
- 4.7 verdictText 생성 규칙 → Task 5 ✓
- 4.8 source.snippet (기존 sourceReference 그대로 사용) → Task 9에서 `new SourceView(rule.getSourceReference())` ✓
- 4.9 EligibilityService 변경 → Task 9 ✓
- 4.10 presentation DTO → Task 10 ✓
- 4.11 API 호환성 (하위호환 안 챙김) → 모든 task가 일관되게 옛 필드 제거 ✓
- 5.1 타입 재정의 → Task 12 ✓
- 5.2 API 레이어 (변경 거의 없음) → 자동
- 5.3 컴포넌트 분해 (5개) → Task 13~15 ✓
- 5.4 색상·아이콘 토큰 → Task 13 (eligibilityStyles.ts) ✓
- 5.5 ?focus= 라우팅 → Task 16 ✓
- 5.6 비로그인/미판정/로딩/결과 분기 → Task 15 EligibilityCard에 그대로 유지 ✓
- 6 테스트 계획 → Task 3,4,5,6,7,11 ✓

### 타입 일관성
- `EligibilityJudgmentResult.overallResult`: Task 8에서 `String`. Task 9 service에서도 `overall.name()`로 String 전달. 일관 ✓
- `CriterionResult.uncertainReason`: Task 8에서 `UncertainReason` (도메인 enum). Task 10 response에서 `String`으로 변환 (`.name()`). 일관 ✓
- `RequirementView`/`UserValueView`/`SourceView`/`SummaryView`: 도메인 view 패키지에 정의(Task 2), application/presentation 모두 그대로 import (Task 8, 10). ✓
- 프론트 타입 (Task 12)과 백엔드 응답 (Task 10) 1:1: `summary{headline,eligibleCount,uncertainCount,ineligibleCount}` ✓ / `criteria{ineligible,uncertain,eligible}` ✓

### Placeholder 스캔
- Task 11 Step 2: "기존 테스트의 어설션을 새 구조에 맞게 갱신" — 구체 패턴 4가지를 명시함 ✓
- Task 17 (수동 스모크 테스트): 실제 기능 검증이라 placeholder 아님 ✓
- Task 18 Step 4: PR 작성은 별도 스킬 위임 (정상) ✓

### 알려진 단순화
- VerdictTextGenerator 조사 처리: v1 `를` 단일. 후속 개선 (spec에 명시됨).
- `region`/`legalDongCode` 필드: Task 3에서 EQ는 코드 그대로 표시 (단위 변환 없음). 행정동 코드 매핑은 후속 작업 (spec에 명시됨).
- formatRange의 `만 ` 중복 제거: `replaceFirst("^만 ", "")` 사용. age 외 enum BETWEEN은 거의 없으므로 충분.
