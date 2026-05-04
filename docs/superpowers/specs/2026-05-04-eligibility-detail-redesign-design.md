# 정책 상세 적합도 카드 사용자 친화 개편 설계

**작성일**: 2026-05-04
**관련 모듈**: backend `eligibility`, `user` / frontend `policy detail`, `personal-info`
**스코프**: 정책 상세 페이지의 적합도 판정 카드 응답 구조 + UI 재설계 (Step 1 백엔드/프론트 재구성 + Step 2 프로필 라우팅 동선)

---

## 1. 배경 및 문제 정의

현재 정책 상세 페이지의 적합도 카드는 `EligibilityJudgmentResponse`를 받아 결과 라벨과 룰별 한 줄 reason 텍스트를 표시한다. 이 구조와 표시 방식은 다음 문제를 가진다.

### 1.1 reason 텍스트가 raw enum을 그대로 노출
`backend/.../eligibility/domain/service/CriterionEvaluation.java:47` 의 `formatValue()`가 `String.valueOf(value)`만 호출하고, `rule.getValue()`도 IN 연산자에서 `"EMPLOYEE,SELF_EMPLOYED,FREELANCER,DAILY_WORKER,PART_TIME"` 같은 raw enum 이름 문자열을 그대로 저장한다.

결과적으로 사용자는 다음과 같은 디버그 로그성 문구를 보게 된다.
```
UNEMPLOYED — 고용 형태(EMPLOYEE,SELF_EMPLOYED,FREELANCER,DAILY_WORKER,PART_TIME) 미충족
```

### 1.2 "왜 이 결과인지" 불투명 (페인 A)
overallResult 라벨만 크게 보이고, 어떤 조건이 결정적이었는지·전체적으로 몇 개를 충족했는지 한눈에 파악되지 않는다.

### 1.3 정책 원문 근거 부재 (페인 B)
`sourceReference`가 `"자격 요건 > 연령 항목"` 같은 단순 텍스트뿐이고, 사용자가 정책 원문에서 그 근거를 직접 확인할 수 없다.

### 1.4 "요구 vs 내 값" 비대조 (페인 D)
정책이 요구하는 값과 내 프로필 값이 한 줄로 합쳐져 있어 비교가 어렵다. 무엇을 요구하는지 / 내가 무엇을 가지고 있는지가 분리되지 않는다.

### 1.5 신뢰도 시각화 부재 (페인 E)
`RuleConfidence(HIGH/MEDIUM/LOW)`가 응답에 노출되지 않고, LOW일 때 `confidenceNote: "근거가 모호함"` 텍스트만 동반된다. 사용자는 UNCERTAIN의 사유(정보 미입력 vs 원문 모호)를 구분할 수 없어 다음 행동을 정하기 어렵다.

---

## 2. 목표 및 비목표

### 2.1 목표
- 적합도 카드의 모든 텍스트가 사람이 읽을 수 있는 자연어로 노출된다.
- 사용자가 "이 정책이 무엇을 요구하는지" 와 "내 값이 무엇인지" 를 한눈에 비교할 수 있다.
- 사용자가 결과의 도출 사유를 첫 인상에서 이해할 수 있다.
- UNCERTAIN의 사유(정보 미입력 / 원문 모호)가 구분 표시되고, 정보 미입력의 경우 프로필 입력 동선을 제공한다.

### 2.2 비목표
- 평가 알고리즘 변경 (AND 로직, LOW→UNCERTAIN 다운그레이드 등 기존 정책 유지).
- 다국어 지원 (한국어 단일).
- 원문 첨부파일·페이지 점프(Q4 단계 분할에서 후속).
- 인라인 프로필 입력 폼(별도 슬라이스로 분리).
- DB 스키마 변경.

---

## 3. 결정 사항 요약

| 항목 | 결정 |
|---|---|
| 응답 그룹핑 | 결과별 (ineligible → uncertain → eligible). 백엔드에서 그룹핑 |
| 헤더 영역 | 결과 라벨 + 한 줄 요약 (`summary.headline`) |
| 신뢰도 표현 | LOW만 "근거 모호" 배지로 표시. HIGH/MEDIUM은 노출하지 않음 |
| UNCERTAIN 사유 분리 | `MISSING_FIELD` / `AMBIGUOUS_SOURCE` 두 종류 |
| 원문 근거 깊이 | snippet 한 줄만. (페이지·attachment 점프는 후속) |
| 충족 그룹 표시 | 기본 접힘. 펼치면 미충족·확인필요와 동일 포맷 풀 표시 |
| MISSING_FIELD CTA 동선 | 프로필 페이지 라우팅 (`/profile?focus={field}`) |
| enum 한국어 라벨 위치 | 도메인 enum의 `displayName` 필드 (한국어 단일) |

---

## 4. 백엔드 설계

### 4.1 도메인 enum 한국어 라벨

`user/domain/model` 의 다음 enum에 `displayName` 필드를 추가한다. 단순 문자열 상수만 추가하므로 도메인 의존 규칙 위반 없음.

대상 enum: `EmploymentKind`, `MaritalStatus`, `Education`, `MajorField`, `SpecializationField`

```java
public enum EmploymentKind {
    EMPLOYEE("직장인"),
    SELF_EMPLOYED("자영업"),
    FREELANCER("프리랜서"),
    DAILY_WORKER("일용직"),
    PART_TIME("아르바이트"),
    UNEMPLOYED("미취업");

    private final String displayName;

    EmploymentKind(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
```

각 enum의 한국어 라벨은 기존 프론트엔드 `EligibilityInfoCard` 가 사용하는 표기와 일관되게 정한다.

### 4.2 RequirementFormatter (도메인 서비스, 신규)

위치: `eligibility/domain/service/RequirementFormatter.java`

책임: `(field, operator, value)` → 한국어 `displayText` 변환.

```java
public class RequirementFormatter {

    public RequirementView format(String field, RuleOperator operator, String value) {
        String displayText = switch (operator) {
            case EQ      -> formatEnumValue(field, value);
            case NOT_EQ  -> formatEnumValue(field, value) + " 제외";
            case GTE     -> formatScalar(field, value) + " 이상";
            case LTE     -> formatScalar(field, value) + " 이하";
            case BETWEEN -> formatRange(field, value);
            case IN      -> formatInList(field, value);
        };
        return new RequirementView(operator.name(), displayText);
    }

    private String formatEnumValue(String field, String raw) { /* 필드별 enum.displayName() */ }
    private String formatScalar(String field, String raw)    { /* "29" → "만 29세", "30000000" → "3,000만 원" */ }
    private String formatRange(String field, String raw)     { /* "19~34" → "만 19세 이상 34세 이하" */ }
    private String formatInList(String field, String raw)    { /* "EMPLOYEE,SELF_EMPLOYED,..." → "재직자(직장인·자영업·...)" */ }
}
```

필드별 단위·라벨 매핑 테이블(예: `age` → "만 N세", `incomeMin/incomeMax/annualIncome` → "N만 원")은 같은 클래스에 상수로 둔다. `legalDongCode` 처럼 코드→이름 변환이 필요한 필드는 v1에서 변환 서비스를 도입하지 않고 룰의 `label`(예: "거주지: 서울특별시 강남구")을 우선 표시한다. 후속 작업으로 행정동 코드 매핑 테이블이 들어오면 그때 formatter에 통합한다.

### 4.3 UserValueFormatter (도메인 서비스, 신규)

위치: `eligibility/domain/service/UserValueFormatter.java`

책임: `(field, rawValue)` → `UserValueView{raw, displayText}`.

```java
public class UserValueFormatter {

    public UserValueView format(String field, Object rawValue) {
        if (rawValue == null) return null;
        String raw = String.valueOf(rawValue);
        String display = switch (field) {
            case "age"             -> "만 " + raw + "세";
            case "employmentKind"  -> EmploymentKind.valueOf(raw).displayName();
            case "maritalStatus"   -> MaritalStatus.valueOf(raw).displayName();
            // ... 다른 필드
            default -> raw;
        };
        return new UserValueView(raw, display);
    }
}
```

### 4.4 SummaryHeadlineGenerator (application 헬퍼, 신규)

위치: `eligibility/application/service/SummaryHeadlineGenerator.java`

책임: 평가 결과 목록 → `summary.headline` 한 줄.

규칙(우선순위):
1. 미충족 1개 → `"{label} 1개 조건이 맞지 않아요"`
2. 미충족 N개 → `"{대표 label} 등 {N}개 조건이 맞지 않아요"`
3. 미충족 0 + 미입력 ≥ 1 → `"{대표 label} 등 {N}개 정보가 더 필요해요"`
4. 미충족 0 + 모호 ≥ 1 → `"정책 원문이 모호한 조건이 {N}개 있어요"`
5. 모두 통과 → `"모든 조건을 충족해요"`

"대표 label"은 첫 번째 룰의 label로 한다(룰 입력 순서가 도메인 의도를 반영한다고 가정).

### 4.5 CriterionEvaluation 변경

기존 `CriterionEvaluation` 레코드는 도메인 평가 결과만 들고 있고, 표시용 필드는 application 레이어에서 합성한다. 즉 `CriterionEvaluation`에는 `requirement`/`userValue`/`verdictText` 같은 표시 필드를 추가하지 않는다.

대신 평가 도메인에서 변경되는 필드:
- `reason` 필드 제거 (display는 application 책임으로 위임)
- `userValue: Object` 필드 신규 (formatter에 넘기기 위함)
- `uncertainReason: UncertainReason enum` 신규 (`MISSING_FIELD`, `AMBIGUOUS_SOURCE`, `null`)

```java
public record CriterionEvaluation(
    String field,
    String label,
    EligibilityResult result,
    Object userValue,                     // 평가 시 추출한 raw 값
    UncertainReason uncertainReason,      // null | MISSING_FIELD | AMBIGUOUS_SOURCE
    EligibilityRule rule,                 // requirement 합성에 필요
    String confidenceNote                 // 기존 유지 (LOW일 때만 채움)
) { ... }
```

`EligibilityEvaluator`의 `eligible() / ineligible() / uncertain() / lowConfidenceUncertain()` 헬퍼는 위 새 형태로 반환한다.

### 4.6 application/dto/result 재설계

```java
public record CriterionResult(
    String field,
    String label,
    String result,
    String uncertainReason,           // "MISSING_FIELD" | "AMBIGUOUS_SOURCE" | null
    RequirementView requirement,
    UserValueView userValue,          // null = 미입력
    String verdictText,
    SourceView source
) {}

public record RequirementView(String operator, String displayText) {}
public record UserValueView(String raw, String displayText) {}
public record SourceView(String snippet) {}

public record GroupedCriteria(
    List<CriterionResult> ineligible,
    List<CriterionResult> uncertain,
    List<CriterionResult> eligible
) {}

public record SummaryView(
    String headline,
    int eligibleCount,
    int uncertainCount,
    int ineligibleCount
) {}

public record EligibilityJudgmentResult(
    Long policyId,
    String policyTitle,
    String overallResult,
    SummaryView summary,
    GroupedCriteria criteria,
    String disclaimer
) {}
```

### 4.7 verdictText 생성 규칙

위치: 새 도메인 서비스 `VerdictTextGenerator` (또는 application 레이어 헬퍼). `CriterionEvaluation` + `RequirementView` + `UserValueView` → 자연어 한 줄.

| 결과 | 템플릿 |
|---|---|
| LIKELY_ELIGIBLE | "{label} 조건을 충족해요" |
| LIKELY_INELIGIBLE | "정책은 {requirement.displayText}을 요구하는데, 내 정보는 {userValue.displayText}이에요" |
| UNCERTAIN + MISSING_FIELD | "{label} 정보가 없어요" |
| UNCERTAIN + AMBIGUOUS_SOURCE | "정책 원문이 모호해 단정하기 어려워요" |

조사(을/를 등)는 v1에서는 단순 처리한다. 구현 시점에 어색한 표현은 템플릿을 다듬는다.

### 4.8 source.snippet 채우기

현재 `EligibilityRule`에는 `sourceReference`만 있고 원문 인용구가 따로 저장되지 않는다. 두 가지 옵션 중 v1은 (a)로 한다.

- (a) **기존 `sourceReference`를 그대로 snippet으로 사용**: 현재 값이 인용구와 비슷한 형태(`"자격 요건 > 연령 항목"` 등)라 만족스럽지 않을 수 있으나, 도메인 모델 변경 없이 진행 가능. 사용자에게는 "정책 원문에서: ___" 라벨만 정확하게 붙여 노출.
- (b) `EligibilityRule`에 `sourceSnippet` 컬럼 신규 + LLM 룰 추출 시 원문 인용구를 함께 추출 (별도 작업).

### 4.9 EligibilityService 변경

```java
public EligibilityJudgmentResult judgeEligibility(Long userId, JudgeEligibilityCommand command) {
    // 1. 기존: 프로필 조회, 정책 조회, 룰 조회, 평가
    List<CriterionEvaluation> evaluations = rules.stream()
        .map(rule -> evaluator.evaluateRule(rule, profile))
        .toList();

    // 2. 신규: 평가 결과를 표시용 CriterionResult로 합성
    List<CriterionResult> results = evaluations.stream()
        .map(this::toCriterionResult)
        .toList();

    // 3. 신규: 결과별 그룹핑
    GroupedCriteria grouped = groupByResult(results);

    // 4. 신규: summary 생성
    SummaryView summary = summaryHeadlineGenerator.generate(evaluations);

    // 5. 전체 결과 결정 (기존 로직 유지)
    EligibilityResult overall = determineOverall(evaluations);

    return new EligibilityJudgmentResult(
        policy.getId(), policy.getTitle(),
        overall.name(), summary, grouped, DISCLAIMER_TEXT
    );
}
```

### 4.10 presentation DTO

`CriterionResponse`, `EligibilityJudgmentResponse`는 `*Result` 와 동일 구조의 record로 두고, application result → response 매핑은 단순 1:1. presentation은 직렬화용 껍데기 역할만.

### 4.11 API 호환성

기존 응답 스키마와 비호환. 모바일 앱 없고 프론트만 있으므로 백엔드/프론트 동시 배포로 처리한다. 하위 호환 필드 유지하지 않는다.

---

## 5. 프론트엔드 설계

### 5.1 타입 재정의 (`frontend/src/types/policy.ts`)

```typescript
export type EligibilityResult = 'LIKELY_ELIGIBLE' | 'UNCERTAIN' | 'LIKELY_INELIGIBLE';
export type UncertainReason = 'MISSING_FIELD' | 'AMBIGUOUS_SOURCE' | null;

export interface RequirementView { operator: string; displayText: string; }
export interface UserValueView { raw: string; displayText: string; }
export interface SourceView { snippet: string | null; }

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

### 5.2 API 레이어
`apis/eligibility.api.ts` 와 `hooks/mutations/useJudgeEligibility.ts` 는 응답 타입만 갱신, 호출 로직은 그대로.

### 5.3 컴포넌트 분해

`PolicyDetailPage.tsx:290~394` 의 인라인 적합도 카드를 분리한다.

```
components/policy/eligibility/
├── EligibilityCard.tsx              # 루트
├── EligibilityHeader.tsx            # 결과 라벨 + summary.headline
├── CriterionGroup.tsx               # ✗ / ⚠ / ✓ 그룹 헤더 + 자식 row
├── CriterionRow.tsx                 # 룰 한 건 카드
├── EligibilityFooter.tsx            # disclaimer + 공식 신청 버튼
└── eligibility.styles.ts            # 결과별 색상·아이콘 매핑
```

각 컴포넌트의 책임:
- **EligibilityCard**: API 결과를 받아 Header / Group×3 / Footer 조립. 로딩·에러·비로그인 분기.
- **EligibilityHeader**: `overallResult` 색상 라벨 + `summary.headline`.
- **CriterionGroup** (`variant: 'ineligible' | 'uncertain' | 'eligible'`):
  - 그룹 헤더: 아이콘 + 라벨(`충족하지 못한 조건` 등) + 카운트
  - eligible variant는 기본 접힘 (`useState`로 토글), 펼치면 미충족·확인필요와 동일 포맷
  - 자식 `CriterionRow` 렌더
- **CriterionRow**: 카드 한 건
  - 라벨 (+ LOW일 때 "근거 모호" 배지: `uncertainReason === 'AMBIGUOUS_SOURCE'`)
  - "정책 요구 / 내 정보" 2열 라벨-값 그리드 (`grid-cols-[auto_1fr] gap-x-3`)
    - 미입력(MISSING_FIELD)이면 내 정보 셀에 `text-neutral-400 italic` 로 "미입력"
  - `verdictText` 본문
  - MISSING_FIELD CTA: 작은 텍스트 링크 "👉 정보 입력하면 더 정확해져요" → `/personal-info?focus={field}` 라우팅
  - 인용구 블록: `source.snippet`이 있을 때만 `📎 "..."` 형태
- **EligibilityFooter**: 기존 disclaimer + 공식 신청 버튼.

### 5.4 색상·아이콘 토큰

```typescript
const RESULT_CONFIG = {
  LIKELY_ELIGIBLE:   { icon: CheckCircle,  color: 'text-success-500', label: '해당 가능성 높음', groupLabel: '충족한 조건' },
  UNCERTAIN:         { icon: AlertCircle,  color: 'text-warning-500', label: '추가 확인 필요',   groupLabel: '추가 확인이 필요한 조건' },
  LIKELY_INELIGIBLE: { icon: XCircle,      color: 'text-error-500',   label: '해당 가능성 낮음', groupLabel: '충족하지 못한 조건' },
};
```

LOW 배지: `inline-flex items-center bg-warning-50 text-warning-700 text-[10px] font-medium rounded px-1.5 py-0.5`.

### 5.5 프로필 페이지 ?focus= 라우팅 (Step 2)

`pages/PersonalInfoPage.tsx` (또는 EligibilityInfoCard가 마운트되는 페이지):

```tsx
const [searchParams] = useSearchParams();
const focusField = searchParams.get('focus');

useEffect(() => {
  if (!focusField) return;
  const el = document.getElementById(`field-${focusField}`);
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    // 첫 번째 입력 요소 포커스
    el.querySelector<HTMLElement>('input, select, button')?.focus();
  }
}, [focusField]);
```

`EligibilityInfoCard` 의 각 필드 섹션에 `id="field-{field명}"` 를 부여한다.

CriterionRow의 CTA 링크는 `<Link to={`/personal-info?focus=${item.field}`}>` 로 라우팅.

### 5.6 비로그인 / 미판정 / 로딩 / 결과 표시 분기

기존 분기 그대로 유지. 결과가 있을 때만 새 카드 구조 사용.

---

## 6. 테스트 계획

### 백엔드
- `RequirementFormatterTest`: 연산자별·필드별 변환 (EQ enum, BETWEEN age, IN enum list, GTE 소득 등). 최소 12개 케이스.
- `UserValueFormatterTest`: 필드별 raw → display 변환.
- `SummaryHeadlineGeneratorTest`: 5가지 우선순위 분기.
- `EligibilityServiceTest` 추가 케이스: 그룹핑 결과·summary count·UNCERTAIN 사유 분리 검증.
- 도메인 enum displayName 회귀 테스트 (각 enum 모든 상수 매핑 누락 방지).
- 기존 `EligibilityEvaluatorTest` 회귀 통과 확인.

### 프론트엔드
- 컴포넌트 분리 후 기존 페이지 렌더링 회귀 (수동 + 가능하면 vitest snapshot).
- CriterionRow 시나리오: LIKELY_ELIGIBLE / LIKELY_INELIGIBLE / UNCERTAIN(MISSING_FIELD) / UNCERTAIN(AMBIGUOUS_SOURCE) 4종 렌더링.
- 충족 그룹 펼치기/접기 토글.
- ?focus= 쿼리로 프로필 페이지 진입 시 스크롤·포커스 동작.

---

## 7. 마이그레이션 / 배포

- DB 변경 없음. 시드(`backend/src/main/resources/sql/2026-05-04-eligibility-rule-policy7-seed.sql`) 그대로 사용.
- API 응답 스키마 변경 → 백엔드/프론트 같은 PR로 묶어 동시 배포.
- 환경 변수·인프라 변경 없음.

---

## 8. 단계 구분 / 향후 작업

본 PR에 포함:
- Step 1: 백엔드 DTO/Formatter 재설계 + 프론트 컴포넌트 분리·재구성
- Step 2: 프로필 페이지 `?focus=` 라우팅 동선

향후 별도 작업 (이번 PR 범위 외):
- (Q4 후속) `source.snippet` 외에 page·attachment 점프
- (CTA 옵션 B) 적합도 카드 안에서 인라인 입력
- 행정동 코드 → 한글 주소 변환
- LLM 룰 추출 시 원문 인용구를 `EligibilityRule.sourceSnippet` 으로 함께 저장

---

## 9. 위험 및 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| `RequirementFormatter` 미커버 enum/필드 누락 | 일부 룰에서 빈 displayText 또는 enum.name() 노출 | 단위 테스트로 모든 enum·필드 커버. 누락 시 fallback으로 raw + 경고 로그. |
| 기존 룰의 `sourceReference` 가 인용구가 아닌 경로/섹션명 형태 | snippet 표시 어색함 | v1에서는 그대로 표시. 후속 작업으로 LLM 추출에 인용구 필드 추가. |
| 프론트 새 컴포넌트 디자인 토큰이 기존 시스템과 어긋남 | 디자인 일관성 깨짐 | 기존 `success/warning/error` 토큰만 사용, 새 색상 도입 금지. |
| 응답 스키마 비호환으로 배포 순서 사고 | 일시적 적합도 카드 깨짐 | PR 단위로 백/프론트 같이 머지. 별 환경에서 미리 통합 테스트. |
