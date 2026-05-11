# 온통청년 정책 상세 보강 + Deterministic 자격 룰 추출 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 온통청년(`getPlcy`) API 응답의 11개 텍스트/구조화 필드를 Policy 컬럼으로 분리 저장하고, rawCodes 코드 필드로부터 deterministic하게 8개 카테고리 EligibilityRule을 생성한다(LLM 호출 없이).

**Architecture:** n8n transform이 신규 11개 필드 + rawCodes를 백엔드로 전송 → IngestPolicyRequest 확장 → policy 테이블 11개 신규 컬럼에 저장. IngestionService.receivePolicy가 rawCodes 있을 때 CodeBasedRuleExtractor를 동기 호출하여 8개 룰을 항상 생성하고 extractionVersion="code-v1"로 마킹. EligibilityRuleGenerationEventListener는 code-v1 룰 존재 시 LLM 호출을 스킵.

**Tech Stack:** Java 21 / Spring Boot 4.0.5 / Hibernate / PostgreSQL 17 / JUnit5 (백엔드), n8n Code 노드 / JavaScript (워크플로우), React 19 / TypeScript / TanStack Query / Tailwind (프론트)

**Spec reference:** `docs/superpowers/specs/DONE_2026-05-09-youth-center-detail-and-deterministic-rules-design.md`

**Branch:** `feat/youth-center-detail-and-deterministic-rules` (이미 main에서 분기됨)

---

## File Structure

| 파일 | 변경 종류 | 책임 |
|---|---|---|
| `backend/src/main/resources/sql/2026-05-09-youth-center-detail-fields.sql` | Create | 마이그레이션 SQL |
| `backend/src/main/java/com/youthfit/eligibility/domain/model/RuleOperator.java` | Modify | ANY enum 추가 |
| `backend/src/main/java/com/youthfit/eligibility/domain/service/EligibilityEvaluator.java` | Modify | ANY 케이스 |
| `backend/src/main/java/com/youthfit/policy/domain/model/Policy.java` | Modify | 11개 컬럼 + builder + updateInfo 확장 |
| `backend/src/main/java/com/youthfit/policy/application/dto/command/RegisterPolicyCommand.java` | Modify | 11개 필드 추가 |
| `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java` | Modify | 11개 필드 빌더/업데이트에 전파 |
| `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java` | Modify | 11개 필드 + from() |
| `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java` | Modify | 11개 필드 + from() |
| `backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java` | Modify | 11개 필드 + RawCodes record |
| `backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java` | Modify | 11개 필드 + RawCodes + toCommand |
| `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java` | Modify | 11개 필드 전파 + CodeBasedRuleExtractionService 동기 호출 |
| `backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java` | Create | rawCodes → List\<EligibilityRule\> 순수 매핑 |
| `backend/src/main/java/com/youthfit/eligibility/application/service/CodeBasedRuleExtractionService.java` | Create | DELETE+INSERT 오케스트레이션 |
| `backend/src/main/java/com/youthfit/eligibility/application/dto/command/CodeBasedExtractionInput.java` | Create | 추출기 입력 값 객체 |
| `backend/src/main/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListener.java` | Modify | code-v1 가드 |
| `backend/src/test/java/com/youthfit/eligibility/domain/service/EligibilityEvaluatorTest.java` | Modify | ANY 케이스 테스트 |
| `backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java` | Create | 8개 카테고리 매핑 테스트 |
| `backend/src/test/java/com/youthfit/eligibility/application/service/CodeBasedRuleExtractionServiceTest.java` | Create | DELETE+INSERT 검증 |
| `backend/src/test/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListenerTest.java` | Modify | code-v1 가드 테스트 |
| `backend/src/test/java/com/youthfit/policy/domain/model/PolicyTest.java` | Modify | 11개 필드 / updateInfo |
| `backend/src/test/java/com/youthfit/policy/application/service/PolicyIngestionServiceTest.java` | Modify | 11개 필드 전파 |
| `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java` | Modify | rawCodes 분기 |
| `n8n/workflows/youth-center-seoul.json` | Modify | transform jsCode 갱신 |
| `frontend/src/types/policy.ts` | Modify | 11개 필드 + ANY 타입 |
| `frontend/src/pages/PolicyDetailPage.tsx` | Modify | 카드 섹션 6-7개 + CTA 버튼 |
| 기존 적합도 결과 컴포넌트(들) | Modify | ANY 룰 ✅ 표시 |
| `/tmp/yc-smoke/run-v2.mjs` | Create | 스모크 스크립트 (workflow transform 검증) |

---

## Phase 0 — 마이그레이션 SQL + 로컬 DB

### Task 0.1: 마이그레이션 SQL 파일 작성

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-09-youth-center-detail-fields.sql`

- [ ] **Step 1: SQL 파일 작성**

```sql
-- 온통청년 정책 상세 정보 보강을 위한 11개 컬럼 추가.
-- 모두 nullable 또는 default 있어 비파괴적이며, BOKJIRO 등 다른 source는 NULL 유지.

ALTER TABLE policy
  ADD COLUMN screening_method TEXT,
  ADD COLUMN submission_documents TEXT,
  ADD COLUMN additional_qualification TEXT,
  ADD COLUMN participation_restriction TEXT,
  ADD COLUMN additional_notes TEXT,
  ADD COLUMN business_period_start DATE,
  ADD COLUMN business_period_end DATE,
  ADD COLUMN business_period_note TEXT,
  ADD COLUMN support_scale INTEGER,
  ADD COLUMN first_come_first_served BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN apply_url VARCHAR(500);
```

- [ ] **Step 2: 커밋**

```bash
git add backend/src/main/resources/sql/2026-05-09-youth-center-detail-fields.sql
git commit -m "feat(be): 정책 상세 11개 컬럼 마이그레이션 SQL"
```

---

### Task 0.2: 로컬 DB에 마이그레이션 적용 + 검증

**Files:** (none — 운영 작업)

- [ ] **Step 1: 로컬 docker postgres가 떠 있는지 확인**

Run: `docker ps --format '{{.Names}}' | grep youthfit-postgres`
Expected: `youthfit-postgres` 한 줄 출력

만약 없으면: `docker compose up -d postgres`

- [ ] **Step 2: SQL 적용**

Run:
```bash
docker exec -i youthfit-postgres psql -U youthfit -d youthfit < backend/src/main/resources/sql/2026-05-09-youth-center-detail-fields.sql
```
Expected: `ALTER TABLE` 출력, 에러 없음

- [ ] **Step 3: 컬럼 존재 검증**

Run:
```bash
docker exec youthfit-postgres psql -U youthfit -d youthfit -c "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name='policy' AND column_name IN ('screening_method','submission_documents','additional_qualification','participation_restriction','additional_notes','business_period_start','business_period_end','business_period_note','support_scale','first_come_first_served','apply_url') ORDER BY column_name;"
```

Expected: 11개 row 출력. `first_come_first_served`만 `is_nullable=NO`, 나머지 모두 `YES`.

- [ ] **Step 4: 기존 row의 default 값 확인**

Run:
```bash
docker exec youthfit-postgres psql -U youthfit -d youthfit -c "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE first_come_first_served = false) AS default_false FROM policy;"
```

Expected: `total = default_false` (모든 기존 row가 default false로 채워짐)

(커밋 없음)

---

## Phase 1 — RuleOperator.ANY 기반

### Task 1.1: RuleOperator.ANY enum 추가 + 단위 테스트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/domain/model/RuleOperator.java`
- Modify: `backend/src/test/java/com/youthfit/eligibility/domain/service/EligibilityEvaluatorTest.java`

- [ ] **Step 1: ANY 케이스 실패 테스트 작성**

`EligibilityEvaluatorTest.java` 끝에 추가:

```java
@Test
void evaluateRule_returns_eligible_when_operator_is_ANY_with_null_user_value() {
    EligibilityRule rule = EligibilityRule.builder()
            .policyId(1L)
            .field("age")
            .operator(RuleOperator.ANY)
            .value("ALL")
            .label("연령")
            .confidence(RuleConfidence.HIGH)
            .extractionVersion("code-v1")
            .build();
    EligibilityProfile profile = mock(EligibilityProfile.class);
    when(profile.getAge()).thenReturn(null);

    CriterionEvaluation evaluation = evaluator.evaluateRule(rule, profile);

    assertThat(evaluation.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
}

@Test
void evaluateRule_returns_eligible_when_operator_is_ANY_with_user_value() {
    EligibilityRule rule = EligibilityRule.builder()
            .policyId(1L)
            .field("maritalStatus")
            .operator(RuleOperator.ANY)
            .value("ALL")
            .label("결혼상태")
            .confidence(RuleConfidence.HIGH)
            .extractionVersion("code-v1")
            .build();
    EligibilityProfile profile = mock(EligibilityProfile.class);
    when(profile.getMaritalStatus()).thenReturn(MaritalStatus.SINGLE);

    CriterionEvaluation evaluation = evaluator.evaluateRule(rule, profile);

    assertThat(evaluation.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests EligibilityEvaluatorTest -i`
Expected: 두 테스트 컴파일 에러 (`RuleOperator.ANY` 심볼 없음)

- [ ] **Step 3: ANY enum 추가**

`RuleOperator.java` 마지막 enum에 `ANY` 추가:

```java
public enum RuleOperator {
    EQ,
    GTE,
    LTE,
    IN,
    BETWEEN,
    NOT_EQ,
    ANY
}
```

- [ ] **Step 4: EligibilityEvaluator에서 ANY 분기 추가**

`EligibilityEvaluator.evaluateRule`의 가장 첫 분기로 ANY 처리:

```java
public CriterionEvaluation evaluateRule(EligibilityRule rule, EligibilityProfile profile) {
    if (rule.getOperator() == RuleOperator.ANY) {
        Object userValue = extractFieldValue(profile, rule.getField());
        return CriterionEvaluation.eligible(rule, userValue);
    }
    Object userValue = extractFieldValue(profile, rule.getField());
    if (userValue == null) {
        return CriterionEvaluation.uncertain(rule);
    }
    if (rule.getConfidence() == RuleConfidence.LOW) {
        return CriterionEvaluation.lowConfidenceUncertain(rule);
    }
    boolean matched = evaluateOperator(rule.getOperator(), userValue, rule.getValue());
    return matched
            ? CriterionEvaluation.eligible(rule, userValue)
            : CriterionEvaluation.ineligible(rule, userValue);
}
```

또한 `evaluateOperator`의 switch에 ANY 케이스 추가 (도달하지 않지만 컴파일 안전):

```java
private boolean evaluateOperator(RuleOperator operator, Object userValue, String ruleValue) {
    return switch (operator) {
        case ANY -> true;
        case EQ -> userValue.toString().equals(ruleValue);
        case NOT_EQ -> !userValue.toString().equals(ruleValue);
        // ... (기존 분기)
    };
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests EligibilityEvaluatorTest -i`
Expected: 모든 테스트 PASS (기존 + 추가 2개)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/model/RuleOperator.java \
        backend/src/main/java/com/youthfit/eligibility/domain/service/EligibilityEvaluator.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/EligibilityEvaluatorTest.java
git commit -m "feat(be): RuleOperator.ANY 추가, EligibilityEvaluator에서 무조건 통과 처리"
```

---

## Phase 2 — Policy 엔티티 확장

### Task 2.1: Policy에 11개 컬럼 추가 + 빌더/updateInfo 확장 + 테스트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/Policy.java`
- Modify: `backend/src/test/java/com/youthfit/policy/domain/model/PolicyTest.java`

- [ ] **Step 1: 신규 필드 빌더 통과 실패 테스트 작성**

`PolicyTest.java`에 추가:

```java
@Test
void builder_assigns_youth_center_detail_fields() {
    Policy policy = Policy.builder()
            .title("샘플")
            .summary("요약")
            .body("본문")
            .category(Category.JOBS)
            .regionCode("서울특별시")
            .screeningMethod("1단계 서류심사")
            .submissionDocuments("주민등록등본")
            .additionalQualification("연 30,000,000원 이하")
            .participationRestriction("기존 수혜자 제외")
            .additionalNotes("우대사항: 장애인")
            .businessPeriodStart(LocalDate.of(2026, 1, 1))
            .businessPeriodEnd(LocalDate.of(2026, 12, 31))
            .businessPeriodNote("상시")
            .supportScale(25)
            .firstComeFirstServed(true)
            .applyUrl("https://example.kr/apply")
            .build();

    assertThat(policy.getScreeningMethod()).isEqualTo("1단계 서류심사");
    assertThat(policy.getSubmissionDocuments()).isEqualTo("주민등록등본");
    assertThat(policy.getAdditionalQualification()).isEqualTo("연 30,000,000원 이하");
    assertThat(policy.getParticipationRestriction()).isEqualTo("기존 수혜자 제외");
    assertThat(policy.getAdditionalNotes()).isEqualTo("우대사항: 장애인");
    assertThat(policy.getBusinessPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(policy.getBusinessPeriodEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(policy.getBusinessPeriodNote()).isEqualTo("상시");
    assertThat(policy.getSupportScale()).isEqualTo(25);
    assertThat(policy.isFirstComeFirstServed()).isTrue();
    assertThat(policy.getApplyUrl()).isEqualTo("https://example.kr/apply");
}

@Test
void updateInfo_replaces_youth_center_detail_fields() {
    Policy policy = Policy.builder()
            .title("샘플")
            .summary("요약")
            .body("본문")
            .category(Category.JOBS)
            .regionCode("서울특별시")
            .build();

    policy.updateInfo(
            "변경된 제목", "변경된 요약", "변경된 본문",
            null, null, null,
            null, null,
            Category.HOUSING, "전국",
            null, null, null, null, null,
            "심사", "서류", "추가자격", "제한", "기타",
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), "특정기간",
            10, true, "https://new.kr"
    );

    assertThat(policy.getScreeningMethod()).isEqualTo("심사");
    assertThat(policy.getSubmissionDocuments()).isEqualTo("서류");
    assertThat(policy.getAdditionalQualification()).isEqualTo("추가자격");
    assertThat(policy.getParticipationRestriction()).isEqualTo("제한");
    assertThat(policy.getAdditionalNotes()).isEqualTo("기타");
    assertThat(policy.getBusinessPeriodStart()).isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(policy.getBusinessPeriodEnd()).isEqualTo(LocalDate.of(2027, 12, 31));
    assertThat(policy.getBusinessPeriodNote()).isEqualTo("특정기간");
    assertThat(policy.getSupportScale()).isEqualTo(10);
    assertThat(policy.isFirstComeFirstServed()).isTrue();
    assertThat(policy.getApplyUrl()).isEqualTo("https://new.kr");
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests PolicyTest -i`
Expected: 컴파일 에러 (필드 없음, builder 메서드 없음, updateInfo 시그니처 불일치)

- [ ] **Step 3: Policy 엔티티에 11개 필드 추가**

`Policy.java` 109줄(`@OneToMany attachments` 위)에 추가:

```java
@Column(name = "screening_method", columnDefinition = "TEXT")
private String screeningMethod;

@Column(name = "submission_documents", columnDefinition = "TEXT")
private String submissionDocuments;

@Column(name = "additional_qualification", columnDefinition = "TEXT")
private String additionalQualification;

@Column(name = "participation_restriction", columnDefinition = "TEXT")
private String participationRestriction;

@Column(name = "additional_notes", columnDefinition = "TEXT")
private String additionalNotes;

@Column(name = "business_period_start")
private LocalDate businessPeriodStart;

@Column(name = "business_period_end")
private LocalDate businessPeriodEnd;

@Column(name = "business_period_note", columnDefinition = "TEXT")
private String businessPeriodNote;

@Column(name = "support_scale")
private Integer supportScale;

@Column(name = "first_come_first_served", nullable = false)
private boolean firstComeFirstServed;

@Column(name = "apply_url", length = 500)
private String applyUrl;
```

- [ ] **Step 4: 빌더 시그니처 확장**

`Policy.java`의 `@Builder private Policy(...)` 시그니처 끝에 11개 파라미터 추가:

```java
@Builder
private Policy(String title, String summary, String body,
               String supportTarget, String selectionCriteria, String supportContent,
               String organization, String contact,
               Category category, String regionCode,
               LocalDate applyStart, LocalDate applyEnd,
               Integer referenceYear, String supportCycle, String provideType,
               // ── 신규 ──
               String screeningMethod, String submissionDocuments,
               String additionalQualification, String participationRestriction,
               String additionalNotes,
               LocalDate businessPeriodStart, LocalDate businessPeriodEnd,
               String businessPeriodNote,
               Integer supportScale, boolean firstComeFirstServed, String applyUrl) {
    this.title = title;
    this.summary = summary;
    this.body = body;
    this.supportTarget = supportTarget;
    this.selectionCriteria = selectionCriteria;
    this.supportContent = supportContent;
    this.organization = organization;
    this.contact = contact;
    this.category = category;
    this.regionCode = regionCode;
    this.applyStart = applyStart;
    this.applyEnd = applyEnd;
    this.referenceYear = referenceYear;
    this.supportCycle = supportCycle;
    this.provideType = provideType;
    this.screeningMethod = screeningMethod;
    this.submissionDocuments = submissionDocuments;
    this.additionalQualification = additionalQualification;
    this.participationRestriction = participationRestriction;
    this.additionalNotes = additionalNotes;
    this.businessPeriodStart = businessPeriodStart;
    this.businessPeriodEnd = businessPeriodEnd;
    this.businessPeriodNote = businessPeriodNote;
    this.supportScale = supportScale;
    this.firstComeFirstServed = firstComeFirstServed;
    this.applyUrl = applyUrl;
    this.status = PolicyStatus.UPCOMING;
    this.detailLevel = DetailLevel.LITE;
}
```

- [ ] **Step 5: updateInfo 시그니처 확장**

```java
public void updateInfo(String title, String summary, String body,
                       String supportTarget, String selectionCriteria, String supportContent,
                       String organization, String contact,
                       Category category, String regionCode,
                       LocalDate applyStart, LocalDate applyEnd,
                       Integer referenceYear, String supportCycle, String provideType,
                       // ── 신규 ──
                       String screeningMethod, String submissionDocuments,
                       String additionalQualification, String participationRestriction,
                       String additionalNotes,
                       LocalDate businessPeriodStart, LocalDate businessPeriodEnd,
                       String businessPeriodNote,
                       Integer supportScale, boolean firstComeFirstServed, String applyUrl) {
    this.title = title;
    this.summary = summary;
    this.body = body;
    this.supportTarget = supportTarget;
    this.selectionCriteria = selectionCriteria;
    this.supportContent = supportContent;
    this.organization = organization;
    this.contact = contact;
    this.category = category;
    this.regionCode = regionCode;
    this.applyStart = applyStart;
    this.applyEnd = applyEnd;
    this.referenceYear = referenceYear;
    this.supportCycle = supportCycle;
    this.provideType = provideType;
    this.screeningMethod = screeningMethod;
    this.submissionDocuments = submissionDocuments;
    this.additionalQualification = additionalQualification;
    this.participationRestriction = participationRestriction;
    this.additionalNotes = additionalNotes;
    this.businessPeriodStart = businessPeriodStart;
    this.businessPeriodEnd = businessPeriodEnd;
    this.businessPeriodNote = businessPeriodNote;
    this.supportScale = supportScale;
    this.firstComeFirstServed = firstComeFirstServed;
    this.applyUrl = applyUrl;
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests PolicyTest -i`
Expected: 모든 PolicyTest PASS (기존 + 추가 2개)

- [ ] **Step 7: 컴파일 안 되는 호출자 확인**

Run: `cd backend && ./gradlew compileJava 2>&1 | grep -E 'updateInfo|Policy.builder' | head -20`
Expected: `PolicyIngestionService.java`에서 빌더/updateInfo 시그니처 불일치 에러 (다음 task에서 수정)

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/Policy.java \
        backend/src/test/java/com/youthfit/policy/domain/model/PolicyTest.java
git commit -m "feat(be): Policy 엔티티에 정책 상세 11개 컬럼 추가 + 빌더/updateInfo 확장"
```

---

## Phase 3 — Policy 애플리케이션 레이어 확장

### Task 3.1: RegisterPolicyCommand에 11개 필드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/command/RegisterPolicyCommand.java`

- [ ] **Step 1: RegisterPolicyCommand 시그니처 확장**

기존 record의 `String sourceHash` 뒤가 아니라 `String provideType` 뒤(기존 정책 필드들 마지막)에 11개 추가하면 IngestionService 매핑이 깔끔. 다음과 같이 재작성:

```java
package com.youthfit.policy.application.dto.command;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.SourceType;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record RegisterPolicyCommand(
        String title,
        String summary,
        String body,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String organization,
        String contact,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        String supportCycle,
        String provideType,
        // ── 신규 정책 상세 11개 ──
        String screeningMethod,
        String submissionDocuments,
        String additionalQualification,
        String participationRestriction,
        String additionalNotes,
        LocalDate businessPeriodStart,
        LocalDate businessPeriodEnd,
        String businessPeriodNote,
        Integer supportScale,
        boolean firstComeFirstServed,
        String applyUrl,
        // ── 기존 ──
        Set<String> lifeTags,
        Set<String> themeTags,
        Set<String> targetTags,
        List<Attachment> attachments,
        List<ReferenceSite> referenceSites,
        List<ApplyMethod> applyMethods,
        SourceType sourceType,
        String externalId,
        String sourceUrl,
        String rawJson,
        String sourceHash
) {
    public record Attachment(String name, String url, String mediaType) {}
    public record ReferenceSite(String name, String url) {}
    public record ApplyMethod(String stageName, String description) {}
}
```

- [ ] **Step 2: 컴파일 확인 (다음 단계 호출자 수정 필요)**

Run: `cd backend && ./gradlew compileJava 2>&1 | grep 'RegisterPolicyCommand' | head -10`
Expected: `IngestionService.java`에서 RegisterPolicyCommand 생성자 인자 수 불일치 에러

(커밋은 Task 3.2와 함께)

---

### Task 3.2: PolicyIngestionService에서 11개 필드 전파 + 테스트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/service/PolicyIngestionServiceTest.java`

- [ ] **Step 1: 신규 필드 전파 실패 테스트 작성**

`PolicyIngestionServiceTest.java`에 추가:

```java
@Test
void registerPolicy_propagates_youth_center_detail_fields_to_new_policy() {
    given(policyRepository.findByNormalizedTitleWithBokjiroSource(any()))
            .willReturn(Optional.empty());
    given(policySourceRepository.findBySourceTypeAndExternalId(any(), any()))
            .willReturn(Optional.empty());
    given(policyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    RegisterPolicyCommand command = sampleRegisterCommandWithDetailFields();
    sut.registerPolicy(command);

    ArgumentCaptor<Policy> policyCaptor = ArgumentCaptor.forClass(Policy.class);
    verify(policyRepository).save(policyCaptor.capture());
    Policy saved = policyCaptor.getValue();
    assertThat(saved.getScreeningMethod()).isEqualTo("심사방법");
    assertThat(saved.getSubmissionDocuments()).isEqualTo("주민등록등본");
    assertThat(saved.getAdditionalQualification()).isEqualTo("추가 자격");
    assertThat(saved.getParticipationRestriction()).isEqualTo("기존 수혜자 제외");
    assertThat(saved.getAdditionalNotes()).isEqualTo("기타");
    assertThat(saved.getBusinessPeriodStart()).isEqualTo(LocalDate.of(2026,1,1));
    assertThat(saved.getBusinessPeriodEnd()).isEqualTo(LocalDate.of(2026,12,31));
    assertThat(saved.getBusinessPeriodNote()).isEqualTo("특정기간");
    assertThat(saved.getSupportScale()).isEqualTo(25);
    assertThat(saved.isFirstComeFirstServed()).isTrue();
    assertThat(saved.getApplyUrl()).isEqualTo("https://apply.kr");
}

private RegisterPolicyCommand sampleRegisterCommandWithDetailFields() {
    return new RegisterPolicyCommand(
            "샘플", "요약", "본문",
            "대상", "기준", "내용",
            "기관", "연락처",
            Category.JOBS, "서울특별시",
            null, null, 2026, "연 1회", "보조금",
            "심사방법", "주민등록등본", "추가 자격", "기존 수혜자 제외", "기타",
            LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), "특정기간",
            25, true, "https://apply.kr",
            Set.of(), Set.of(), Set.of(),
            List.of(), List.of(), List.of(),
            SourceType.YOUTH_CENTER, "EXT-001", "https://src.kr",
            "{}", "abc123"
    );
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests PolicyIngestionServiceTest -i`
Expected: 컴파일 에러 (`RegisterPolicyCommand` 인자 수 불일치)

- [ ] **Step 3: PolicyIngestionService.registerPolicy 빌더 호출에 11개 필드 추가**

`Policy.builder()` 호출 부분에 추가:

```java
Policy policy = Policy.builder()
        .title(command.title())
        .summary(command.summary())
        .body(command.body())
        .supportTarget(command.supportTarget())
        .selectionCriteria(command.selectionCriteria())
        .supportContent(command.supportContent())
        .organization(command.organization())
        .contact(command.contact())
        .category(command.category())
        .regionCode(command.regionCode())
        .applyStart(command.applyStart())
        .applyEnd(command.applyEnd())
        .referenceYear(command.referenceYear())
        .supportCycle(command.supportCycle())
        .provideType(command.provideType())
        .screeningMethod(command.screeningMethod())
        .submissionDocuments(command.submissionDocuments())
        .additionalQualification(command.additionalQualification())
        .participationRestriction(command.participationRestriction())
        .additionalNotes(command.additionalNotes())
        .businessPeriodStart(command.businessPeriodStart())
        .businessPeriodEnd(command.businessPeriodEnd())
        .businessPeriodNote(command.businessPeriodNote())
        .supportScale(command.supportScale())
        .firstComeFirstServed(command.firstComeFirstServed())
        .applyUrl(command.applyUrl())
        .build();
```

- [ ] **Step 4: PolicyIngestionService.registerPolicy의 updateInfo 호출도 확장**

```java
policy.updateInfo(
        command.title(),
        command.summary(),
        command.body(),
        command.supportTarget(),
        command.selectionCriteria(),
        command.supportContent(),
        command.organization(),
        command.contact(),
        command.category(),
        command.regionCode(),
        command.applyStart(),
        command.applyEnd(),
        command.referenceYear(),
        command.supportCycle(),
        command.provideType(),
        command.screeningMethod(),
        command.submissionDocuments(),
        command.additionalQualification(),
        command.participationRestriction(),
        command.additionalNotes(),
        command.businessPeriodStart(),
        command.businessPeriodEnd(),
        command.businessPeriodNote(),
        command.supportScale(),
        command.firstComeFirstServed(),
        command.applyUrl()
);
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests PolicyIngestionServiceTest -i`
Expected: 모든 PolicyIngestionServiceTest PASS

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/dto/command/RegisterPolicyCommand.java \
        backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java \
        backend/src/test/java/com/youthfit/policy/application/service/PolicyIngestionServiceTest.java
git commit -m "feat(be): RegisterPolicyCommand·PolicyIngestionService에 정책 상세 11개 필드 전파"
```

---

## Phase 4 — Policy Result/Response 확장 (프론트 직렬화)

### Task 4.1: PolicyDetailResult에 11개 필드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java`

- [ ] **Step 1: PolicyDetailResult 시그니처/`from()` 확장**

전체 record를 다음과 같이 재작성 (기존 from() 끝에 11개 getter 추가):

```java
public record PolicyDetailResult(
        Long id,
        String title,
        String summary,
        String body,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String organization,
        String contact,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        String supportCycle,
        String provideType,
        // ── 신규 ──
        String screeningMethod,
        String submissionDocuments,
        String additionalQualification,
        String participationRestriction,
        String additionalNotes,
        LocalDate businessPeriodStart,
        LocalDate businessPeriodEnd,
        String businessPeriodNote,
        Integer supportScale,
        boolean firstComeFirstServed,
        String applyUrl,
        // ── 기존 ──
        PolicyStatus status,
        DetailLevel detailLevel,
        Set<String> lifeTags,
        Set<String> themeTags,
        Set<String> targetTags,
        List<Attachment> attachments,
        List<ReferenceSite> referenceSites,
        List<ApplyMethod> applyMethods,
        SourceType sourceType,
        String sourceLabel,
        String sourceUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    // 내부 record (기존 그대로)
    public record Attachment(Long id, String name, String url, String mediaType) {
        public static Attachment from(PolicyAttachment attachment) {
            return new Attachment(
                    attachment.getId(),
                    attachment.getName(),
                    attachment.getUrl(),
                    attachment.getMediaType());
        }
    }

    public record ReferenceSite(String name, String url) {
        public static ReferenceSite from(PolicyReferenceSite site) {
            return new ReferenceSite(site.name(), site.url());
        }
    }

    public record ApplyMethod(String stageName, String description) {
        public static ApplyMethod from(PolicyApplyMethod method) {
            return new ApplyMethod(method.stageName(), method.description());
        }
    }

    public static PolicyDetailResult from(Policy policy, PolicySource source) {
        SourceType sourceType = source != null ? source.getSourceType() : null;
        String sourceLabel = sourceType != null ? sourceType.getLabel() : null;
        String sourceUrl = source != null ? source.getSourceUrl() : null;
        return new PolicyDetailResult(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getBody(),
                policy.getSupportTarget(),
                policy.getSelectionCriteria(),
                policy.getSupportContent(),
                policy.getOrganization(),
                policy.getContact(),
                policy.getCategory(),
                policy.getRegionCode(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getReferenceYear(),
                policy.getSupportCycle(),
                policy.getProvideType(),
                policy.getScreeningMethod(),
                policy.getSubmissionDocuments(),
                policy.getAdditionalQualification(),
                policy.getParticipationRestriction(),
                policy.getAdditionalNotes(),
                policy.getBusinessPeriodStart(),
                policy.getBusinessPeriodEnd(),
                policy.getBusinessPeriodNote(),
                policy.getSupportScale(),
                policy.isFirstComeFirstServed(),
                policy.getApplyUrl(),
                policy.getStatus(),
                policy.getDetailLevel(),
                Set.copyOf(policy.getLifeTags()),
                Set.copyOf(policy.getThemeTags()),
                Set.copyOf(policy.getTargetTags()),
                policy.getAttachments().stream().map(Attachment::from).toList(),
                policy.getReferenceSites().stream().map(ReferenceSite::from).toList(),
                policy.getApplyMethods().stream().map(ApplyMethod::from).toList(),
                sourceType,
                sourceLabel,
                sourceUrl,
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
```

(커밋은 Task 4.2와 함께)

---

### Task 4.2: PolicyDetailResponse에 11개 필드 추가 + 컴파일 확인

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java`

- [ ] **Step 1: PolicyDetailResponse 시그니처/`from()` 확장**

```java
public record PolicyDetailResponse(
        Long id,
        String title,
        String summary,
        String body,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String organization,
        String contact,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        String supportCycle,
        String provideType,
        // ── 신규 ──
        String screeningMethod,
        String submissionDocuments,
        String additionalQualification,
        String participationRestriction,
        String additionalNotes,
        LocalDate businessPeriodStart,
        LocalDate businessPeriodEnd,
        String businessPeriodNote,
        Integer supportScale,
        boolean firstComeFirstServed,
        String applyUrl,
        // ── 기존 ──
        PolicyStatus status,
        DetailLevel detailLevel,
        Set<String> lifeTags,
        Set<String> themeTags,
        Set<String> targetTags,
        List<Attachment> attachments,
        List<ReferenceSite> referenceSites,
        List<ApplyMethod> applyMethods,
        SourceType sourceType,
        String sourceLabel,
        String sourceUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Attachment(Long id, String name, String url, String mediaType) {
        static Attachment from(PolicyDetailResult.Attachment a) {
            return new Attachment(a.id(), a.name(), a.url(), a.mediaType());
        }
    }

    public record ReferenceSite(String name, String url) {
        static ReferenceSite from(PolicyDetailResult.ReferenceSite s) {
            return new ReferenceSite(s.name(), s.url());
        }
    }

    public record ApplyMethod(String stageName, String description) {
        static ApplyMethod from(PolicyDetailResult.ApplyMethod m) {
            return new ApplyMethod(m.stageName(), m.description());
        }
    }

    public static PolicyDetailResponse from(PolicyDetailResult result) {
        return new PolicyDetailResponse(
                result.id(),
                result.title(),
                result.summary(),
                result.body(),
                result.supportTarget(),
                result.selectionCriteria(),
                result.supportContent(),
                result.organization(),
                result.contact(),
                result.category(),
                result.regionCode(),
                result.applyStart(),
                result.applyEnd(),
                result.referenceYear(),
                result.supportCycle(),
                result.provideType(),
                result.screeningMethod(),
                result.submissionDocuments(),
                result.additionalQualification(),
                result.participationRestriction(),
                result.additionalNotes(),
                result.businessPeriodStart(),
                result.businessPeriodEnd(),
                result.businessPeriodNote(),
                result.supportScale(),
                result.firstComeFirstServed(),
                result.applyUrl(),
                result.status(),
                result.detailLevel(),
                result.lifeTags(),
                result.themeTags(),
                result.targetTags(),
                result.attachments().stream().map(Attachment::from).toList(),
                result.referenceSites().stream().map(ReferenceSite::from).toList(),
                result.applyMethods().stream().map(ApplyMethod::from).toList(),
                result.sourceType(),
                result.sourceLabel(),
                result.sourceUrl(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java \
        backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java
git commit -m "feat(be): PolicyDetailResult·Response에 정책 상세 11개 필드 추가"
```

---

## Phase 5 — Ingestion DTO/Command 확장

### Task 5.1: IngestPolicyCommand에 RawCodes + 11개 필드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java`

- [ ] **Step 1: IngestPolicyCommand 재작성**

```java
package com.youthfit.ingestion.application.dto.command;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record IngestPolicyCommand(
        String sourceUrl,
        String sourceType,
        LocalDateTime fetchedAt,
        String externalId,
        String title,
        String summary,
        String body,
        String category,
        String region,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        String supportCycle,
        String provideType,
        String organization,
        String contact,
        List<String> lifeTags,
        List<String> themeTags,
        List<String> targetTags,
        List<Attachment> attachments,
        List<ReferenceSite> referenceSites,
        List<ApplyMethod> applyMethods,
        // ── 신규 텍스트/구조화 11개 ──
        String screeningMethod,
        String submissionDocuments,
        String additionalQualification,
        String participationRestriction,
        String additionalNotes,
        LocalDate businessPeriodStart,
        LocalDate businessPeriodEnd,
        String businessPeriodNote,
        Integer supportScale,
        Boolean firstComeFirstServed,
        String applyUrl,
        // ── 신규 rawCodes (deterministic rule용) ──
        RawCodes rawCodes
) {
    public record Attachment(String name, String url, String mediaType) {}
    public record ReferenceSite(String name, String url) {}
    public record ApplyMethod(String stageName, String description) {}

    public record RawCodes(
            Integer ageMin,
            Integer ageMax,
            String ageLimitYn,
            String maritalStatusCd,
            String earnConditionCd,
            Integer earnMin,
            Integer earnMax,
            String earnEtcCn,
            String employmentKindCd,
            String educationCd,
            String majorFieldCd,
            String specializationCd,
            List<String> zipCodes
    ) {}
}
```

(커밋은 Task 5.2와 함께)

---

### Task 5.2: IngestPolicyRequest.RawData에 RawCodes + 11개 필드 추가 + toCommand 매핑

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java`

- [ ] **Step 1: IngestPolicyRequest 재작성**

```java
package com.youthfit.ingestion.presentation.dto.request;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record IngestPolicyRequest(
        @NotNull @Valid SourceInfo source,
        @NotNull @Valid RawData rawData
) {

    public IngestPolicyCommand toCommand() {
        return new IngestPolicyCommand(
                source.url(),
                source.type(),
                source.fetchedAt(),
                rawData.externalId(),
                rawData.title(),
                rawData.summary(),
                rawData.body(),
                rawData.category(),
                rawData.region(),
                rawData.applyStart(),
                rawData.applyEnd(),
                rawData.referenceYear(),
                rawData.supportCycle(),
                rawData.provideType(),
                rawData.organization(),
                rawData.contact(),
                rawData.lifeTags(),
                rawData.themeTags(),
                rawData.targetTags(),
                rawData.attachments() == null ? List.of() : rawData.attachments().stream()
                        .map(a -> new IngestPolicyCommand.Attachment(a.name(), a.url(), a.mediaType()))
                        .toList(),
                rawData.referenceSites() == null ? List.of() : rawData.referenceSites().stream()
                        .map(s -> new IngestPolicyCommand.ReferenceSite(s.name(), s.url()))
                        .toList(),
                rawData.applyMethods() == null ? List.of() : rawData.applyMethods().stream()
                        .map(m -> new IngestPolicyCommand.ApplyMethod(m.stageName(), m.description()))
                        .toList(),
                rawData.screeningMethod(),
                rawData.submissionDocuments(),
                rawData.additionalQualification(),
                rawData.participationRestriction(),
                rawData.additionalNotes(),
                rawData.businessPeriodStart(),
                rawData.businessPeriodEnd(),
                rawData.businessPeriodNote(),
                rawData.supportScale(),
                rawData.firstComeFirstServed(),
                rawData.applyUrl(),
                rawData.rawCodes() == null ? null : new IngestPolicyCommand.RawCodes(
                        rawData.rawCodes().ageMin(),
                        rawData.rawCodes().ageMax(),
                        rawData.rawCodes().ageLimitYn(),
                        rawData.rawCodes().maritalStatusCd(),
                        rawData.rawCodes().earnConditionCd(),
                        rawData.rawCodes().earnMin(),
                        rawData.rawCodes().earnMax(),
                        rawData.rawCodes().earnEtcCn(),
                        rawData.rawCodes().employmentKindCd(),
                        rawData.rawCodes().educationCd(),
                        rawData.rawCodes().majorFieldCd(),
                        rawData.rawCodes().specializationCd(),
                        rawData.rawCodes().zipCodes() == null ? List.of() : rawData.rawCodes().zipCodes()
                )
        );
    }

    public record SourceInfo(
            @NotBlank String url,
            @NotBlank String type,
            @NotNull LocalDateTime fetchedAt
    ) {}

    public record RawData(
            String externalId,
            @NotBlank String title,
            String summary,
            @NotBlank String body,
            @NotBlank String category,
            @NotBlank String region,
            LocalDate applyStart,
            LocalDate applyEnd,
            Integer referenceYear,
            String supportCycle,
            String provideType,
            String organization,
            String contact,
            List<String> lifeTags,
            List<String> themeTags,
            List<String> targetTags,
            List<@Valid Attachment> attachments,
            List<@Valid ReferenceSite> referenceSites,
            List<@Valid ApplyMethod> applyMethods,
            // ── 신규 11개 ──
            String screeningMethod,
            String submissionDocuments,
            String additionalQualification,
            String participationRestriction,
            String additionalNotes,
            LocalDate businessPeriodStart,
            LocalDate businessPeriodEnd,
            String businessPeriodNote,
            Integer supportScale,
            Boolean firstComeFirstServed,
            String applyUrl,
            @Valid RawCodes rawCodes
    ) {}

    public record Attachment(
            @NotBlank String name,
            @NotBlank String url,
            String mediaType
    ) {}

    public record ReferenceSite(
            @NotBlank String name,
            @NotBlank String url
    ) {}

    public record ApplyMethod(
            @NotBlank String stageName,
            String description
    ) {}

    public record RawCodes(
            Integer ageMin,
            Integer ageMax,
            String ageLimitYn,
            String maritalStatusCd,
            String earnConditionCd,
            Integer earnMin,
            Integer earnMax,
            String earnEtcCn,
            String employmentKindCd,
            String educationCd,
            String majorFieldCd,
            String specializationCd,
            List<String> zipCodes
    ) {}
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava 2>&1 | grep -E '에러|error' | head -10`
Expected: `IngestionService.java`에서 RegisterPolicyCommand 생성자 인자 수 불일치 (다음 task)

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java \
        backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java
git commit -m "feat(be): IngestPolicyRequest/Command에 정책 상세 11개 필드 + RawCodes 추가"
```

---

### Task 5.3: IngestionService에서 RegisterPolicyCommand 생성에 11개 필드 전파 (extractor wiring 제외)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`
- Modify: `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java`

- [ ] **Step 1: 신규 필드 전파 실패 테스트 작성**

`IngestionServiceTest.java`에 추가:

```java
@Test
void receivePolicy_propagates_youth_center_detail_fields_to_register_command() {
    IngestPolicyCommand command = sampleCommandWithDetailFields();
    given(policyIngestionService.registerPolicy(any()))
            .willReturn(PolicyIngestionResult.registered(99L));

    sut.receivePolicy(command);

    ArgumentCaptor<RegisterPolicyCommand> captor = ArgumentCaptor.forClass(RegisterPolicyCommand.class);
    verify(policyIngestionService).registerPolicy(captor.capture());
    RegisterPolicyCommand reg = captor.getValue();
    assertThat(reg.screeningMethod()).isEqualTo("심사방법");
    assertThat(reg.submissionDocuments()).isEqualTo("주민등록등본");
    assertThat(reg.additionalQualification()).isEqualTo("추가 자격");
    assertThat(reg.participationRestriction()).isEqualTo("기존 수혜자 제외");
    assertThat(reg.additionalNotes()).isEqualTo("기타");
    assertThat(reg.businessPeriodStart()).isEqualTo(LocalDate.of(2026,1,1));
    assertThat(reg.businessPeriodEnd()).isEqualTo(LocalDate.of(2026,12,31));
    assertThat(reg.businessPeriodNote()).isEqualTo("특정기간");
    assertThat(reg.supportScale()).isEqualTo(25);
    assertThat(reg.firstComeFirstServed()).isTrue();
    assertThat(reg.applyUrl()).isEqualTo("https://apply.kr");
}

private IngestPolicyCommand sampleCommandWithDetailFields() {
    return new IngestPolicyCommand(
            "https://src.kr", "YOUTH_CENTER", LocalDateTime.now(),
            "EXT-1", "제목", "요약", "[지원대상]\n내용", "복지", "서울특별시",
            null, null, 2026, null, "보조금",
            "기관", "연락처",
            List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(),
            "심사방법", "주민등록등본", "추가 자격", "기존 수혜자 제외", "기타",
            LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), "특정기간",
            25, true, "https://apply.kr",
            null
    );
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests IngestionServiceTest -i`
Expected: 컴파일 에러 (RegisterPolicyCommand 생성자 인자 부족)

- [ ] **Step 3: IngestionService.receivePolicy의 RegisterPolicyCommand 생성에 11개 필드 추가**

`new RegisterPolicyCommand(...)` 호출에 신규 11개 인자 (`provideType` 다음, 기존 `lifeTags` 직전):

```java
RegisterPolicyCommand registerCommand = new RegisterPolicyCommand(
        command.title(),
        summary,
        command.body(),
        sections.supportTarget(),
        sections.selectionCriteria(),
        sections.supportContent(),
        command.organization(),
        command.contact(),
        category,
        command.region(),
        period.start(),
        period.end(),
        command.referenceYear(),
        command.supportCycle(),
        command.provideType(),
        // ── 신규 ──
        command.screeningMethod(),
        command.submissionDocuments(),
        command.additionalQualification(),
        command.participationRestriction(),
        command.additionalNotes(),
        command.businessPeriodStart(),
        command.businessPeriodEnd(),
        command.businessPeriodNote(),
        command.supportScale(),
        Boolean.TRUE.equals(command.firstComeFirstServed()),
        command.applyUrl(),
        // ── 기존 ──
        toSet(command.lifeTags()),
        toSet(command.themeTags()),
        toSet(command.targetTags()),
        mapAttachments(command.attachments()),
        mapReferenceSites(command.referenceSites()),
        mapApplyMethods(command.applyMethods()),
        sourceType,
        externalId,
        command.sourceUrl(),
        rawJson,
        sourceHash
);
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests IngestionServiceTest -i`
Expected: 모든 IngestionServiceTest PASS

- [ ] **Step 5: 컴파일 확인 (전체)**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java
git commit -m "feat(be): IngestionService에서 RegisterPolicyCommand에 정책 상세 11개 필드 전파"
```

---

## Phase 6 — CodeBasedRuleExtractor (TDD per category)

### Task 6.1: CodeBasedExtractionInput 값 객체 + 빈 추출기 스켈레톤

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/application/dto/command/CodeBasedExtractionInput.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java`
- Create: `backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java`

- [ ] **Step 1: CodeBasedExtractionInput 작성**

```java
package com.youthfit.eligibility.application.dto.command;

import java.util.List;

public record CodeBasedExtractionInput(
        Integer ageMin,
        Integer ageMax,
        String ageLimitYn,
        String maritalStatusCd,
        String earnConditionCd,
        Integer earnMin,
        Integer earnMax,
        String earnEtcCn,
        String employmentKindCd,
        String educationCd,
        String majorFieldCd,
        String specializationCd,
        List<String> zipCodes
) {}
```

- [ ] **Step 2: 빈 CodeBasedRuleExtractor 작성**

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;

import java.util.List;

public class CodeBasedRuleExtractor {

    public static final String EXTRACTION_VERSION = "code-v1";

    public List<EligibilityRule> extract(Long policyId, CodeBasedExtractionInput input) {
        // Phase 6.2 ~ 6.9에서 카테고리별로 채워나감
        return List.of();
    }
}
```

- [ ] **Step 3: 테스트 클래스 스켈레톤**

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeBasedRuleExtractorTest {

    private final CodeBasedRuleExtractor sut = new CodeBasedRuleExtractor();

    private CodeBasedExtractionInput input(
            Integer ageMin, Integer ageMax, String ageLimitYn,
            String mrgSttsCd, String earnCndSeCd, Integer earnMin, Integer earnMax, String earnEtcCn,
            String jobCd, String schoolCd, String plcyMajorCd, String sbizCd,
            List<String> zipCodes) {
        return new CodeBasedExtractionInput(
                ageMin, ageMax, ageLimitYn,
                mrgSttsCd, earnCndSeCd, earnMin, earnMax, earnEtcCn,
                jobCd, schoolCd, plcyMajorCd, sbizCd,
                zipCodes == null ? List.of() : zipCodes);
    }

    private EligibilityRule findRule(List<EligibilityRule> rules, String field) {
        return rules.stream()
                .filter(r -> r.getField().equals(field))
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule not found: " + field));
    }

    @Test
    void extract_always_returns_8_rules() {
        List<EligibilityRule> rules = sut.extract(1L,
                input(null,null,null, null,null,null,null,null,
                      null,null,null,null, List.of()));
        assertThat(rules).hasSize(8);
        assertThat(rules).allSatisfy(r -> {
            assertThat(r.getPolicyId()).isEqualTo(1L);
            assertThat(r.getConfidence()).isEqualTo(RuleConfidence.HIGH);
            assertThat(r.getExtractionVersion()).isEqualTo("code-v1");
        });
    }
}
```

- [ ] **Step 4: 첫 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: `extract_always_returns_8_rules` FAIL — `Expected size: 8 but was: 0`

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/dto/command/CodeBasedExtractionInput.java \
        backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java
git commit -m "feat(be): CodeBasedRuleExtractor 스켈레톤 + 입력 값 객체"
```

---

### Task 6.2: age 카테고리 매핑

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java`
- Modify: `backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java`

- [ ] **Step 1: age 카테고리 5개 분기 실패 테스트 작성**

`CodeBasedRuleExtractorTest`에 추가:

```java
@Test
void age_returns_ANY_when_ageLimitYn_is_N() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(15, 40, "N", null, null, null, null, null, null, null, null, null, List.of()));
    EligibilityRule age = findRule(rules, "age");
    assertThat(age.getOperator()).isEqualTo(RuleOperator.ANY);
    assertThat(age.getValue()).isEqualTo("ALL");
    assertThat(age.getLabel()).isEqualTo("연령");
    assertThat(age.getSourceReference()).isEqualTo("getPlcy.sprtTrgtAgeLmtYn: N");
}

@Test
void age_returns_BETWEEN_when_min_and_max_present() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(19, 34, "Y", null, null, null, null, null, null, null, null, null, List.of()));
    EligibilityRule age = findRule(rules, "age");
    assertThat(age.getOperator()).isEqualTo(RuleOperator.BETWEEN);
    assertThat(age.getValue()).isEqualTo("19~34");
}

@Test
void age_returns_GTE_when_only_min_present() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(19, null, "Y", null, null, null, null, null, null, null, null, null, List.of()));
    EligibilityRule age = findRule(rules, "age");
    assertThat(age.getOperator()).isEqualTo(RuleOperator.GTE);
    assertThat(age.getValue()).isEqualTo("19");
}

@Test
void age_returns_LTE_when_only_max_present() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null, 34, "Y", null, null, null, null, null, null, null, null, null, List.of()));
    EligibilityRule age = findRule(rules, "age");
    assertThat(age.getOperator()).isEqualTo(RuleOperator.LTE);
    assertThat(age.getValue()).isEqualTo("34");
}

@Test
void age_returns_ANY_when_all_null() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null, null, null, null, null, null, null, null, null, null, null, null, List.of()));
    EligibilityRule age = findRule(rules, "age");
    assertThat(age.getOperator()).isEqualTo(RuleOperator.ANY);
    assertThat(age.getValue()).isEqualTo("ALL");
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: 5개 신규 테스트 + 기존 1개(8 룰) FAIL

- [ ] **Step 3: extract()에 age 분기 + 8 카테고리 placeholder 추가**

`CodeBasedRuleExtractor.java`:

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;

import java.util.ArrayList;
import java.util.List;

public class CodeBasedRuleExtractor {

    public static final String EXTRACTION_VERSION = "code-v1";

    public List<EligibilityRule> extract(Long policyId, CodeBasedExtractionInput input) {
        List<EligibilityRule> rules = new ArrayList<>();
        rules.add(buildAgeRule(policyId, input));
        rules.add(anyRule(policyId, "maritalStatus", "결혼상태", "ALL", "code-v1: 미구현 (Task 6.3에서 교체)"));
        rules.add(anyRule(policyId, "annualIncome", "연소득", "ALL", "code-v1: 미구현 (Task 6.4에서 교체)"));
        rules.add(anyRule(policyId, "employmentKind", "취업상태", "ALL", "code-v1: 미구현 (Task 6.5에서 교체)"));
        rules.add(anyRule(policyId, "education", "학력", "ALL", "code-v1: 미구현 (Task 6.6에서 교체)"));
        rules.add(anyRule(policyId, "majorField", "전공", "ALL", "code-v1: 미구현 (Task 6.7에서 교체)"));
        rules.add(anyRule(policyId, "specializationField", "특화요건", "ALL", "code-v1: 미구현 (Task 6.8에서 교체)"));
        rules.add(anyRule(policyId, "region", "거주지", "ALL", "code-v1: 미구현 (Task 6.9에서 교체)"));
        return rules;
    }

    // ── age ──

    private EligibilityRule buildAgeRule(Long policyId, CodeBasedExtractionInput input) {
        if ("N".equals(input.ageLimitYn())) {
            return rule(policyId, "age", "연령", RuleOperator.ANY, "ALL",
                    "getPlcy.sprtTrgtAgeLmtYn: N");
        }
        Integer min = input.ageMin();
        Integer max = input.ageMax();
        if (min != null && min > 0 && max != null && max > 0) {
            return rule(policyId, "age", "연령", RuleOperator.BETWEEN, min + "~" + max,
                    "getPlcy.sprtTrgtMinAge: " + min + ", sprtTrgtMaxAge: " + max);
        }
        if (min != null && min > 0) {
            return rule(policyId, "age", "연령", RuleOperator.GTE, String.valueOf(min),
                    "getPlcy.sprtTrgtMinAge: " + min);
        }
        if (max != null && max > 0) {
            return rule(policyId, "age", "연령", RuleOperator.LTE, String.valueOf(max),
                    "getPlcy.sprtTrgtMaxAge: " + max);
        }
        return rule(policyId, "age", "연령", RuleOperator.ANY, "ALL",
                "getPlcy.sprtTrgtMinAge·sprtTrgtMaxAge: 미지정");
    }

    // ── 빌더 헬퍼 ──

    private EligibilityRule rule(Long policyId, String field, String label,
                                  RuleOperator operator, String value, String sourceRef) {
        return EligibilityRule.builder()
                .policyId(policyId)
                .field(field)
                .operator(operator)
                .value(value)
                .label(label)
                .sourceReference(sourceRef)
                .confidence(RuleConfidence.HIGH)
                .extractionVersion(EXTRACTION_VERSION)
                .build();
    }

    private EligibilityRule anyRule(Long policyId, String field, String label, String value, String sourceRef) {
        return rule(policyId, field, label, RuleOperator.ANY, value, sourceRef);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: age 5개 + 기본 1개 PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java
git commit -m "feat(be): CodeBasedRuleExtractor age 카테고리 구현 (TDD)"
```

---

### Task 6.3: maritalStatus 카테고리

**Files:** (Task 6.2와 동일한 두 파일)

- [ ] **Step 1: maritalStatus 4개 분기 실패 테스트 작성**

```java
@Test
void maritalStatus_returns_EQ_MARRIED_for_0055001() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, "0055001", null,null,null,null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "maritalStatus");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.EQ);
    assertThat(r.getValue()).isEqualTo("MARRIED");
    assertThat(r.getLabel()).isEqualTo("결혼상태");
    assertThat(r.getSourceReference()).isEqualTo("getPlcy.mrgSttsCd: 0055001");
}

@Test
void maritalStatus_returns_EQ_SINGLE_for_0055002() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, "0055002", null,null,null,null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "maritalStatus");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.EQ);
    assertThat(r.getValue()).isEqualTo("SINGLE");
}

@Test
void maritalStatus_returns_ANY_for_0055003() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, "0055003", null,null,null,null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "maritalStatus");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
}

@Test
void maritalStatus_returns_ANY_for_null() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "maritalStatus");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
    assertThat(r.getSourceReference()).isEqualTo("getPlcy.mrgSttsCd: 미지정");
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: 4개 신규 FAIL

- [ ] **Step 3: maritalStatus 분기 구현**

`CodeBasedRuleExtractor`의 placeholder line `rules.add(anyRule(... "maritalStatus" ...))`를 다음으로 교체:

```java
rules.add(buildMaritalStatusRule(policyId, input));
```

그리고 메서드 추가:

```java
private EligibilityRule buildMaritalStatusRule(Long policyId, CodeBasedExtractionInput input) {
    String code = input.maritalStatusCd();
    String src = "getPlcy.mrgSttsCd: " + (code == null ? "미지정" : code);
    if ("0055001".equals(code)) {
        return rule(policyId, "maritalStatus", "결혼상태", RuleOperator.EQ, "MARRIED", src);
    }
    if ("0055002".equals(code)) {
        return rule(policyId, "maritalStatus", "결혼상태", RuleOperator.EQ, "SINGLE", src);
    }
    return rule(policyId, "maritalStatus", "결혼상태", RuleOperator.ANY, "ALL", src);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: maritalStatus 4개 PASS, 다른 placeholder 카테고리 그대로

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java
git commit -m "feat(be): CodeBasedRuleExtractor maritalStatus 카테고리 구현 (TDD)"
```

---

### Task 6.4: annualIncome 카테고리

**Files:** (위 두 파일)

- [ ] **Step 1: annualIncome 5개 분기 실패 테스트 작성**

```java
@Test
void annualIncome_returns_ANY_for_0043001_independent_condition() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, "0043001", null,null,null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "annualIncome");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
    assertThat(r.getValue()).isEqualTo("ALL");
}

@Test
void annualIncome_returns_BETWEEN_when_0043002_with_min_and_max() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, "0043002", 0, 32_000_000, null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "annualIncome");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.BETWEEN);
    assertThat(r.getValue()).isEqualTo("0~32000000");
}

@Test
void annualIncome_returns_LTE_when_0043002_with_only_max() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, "0043002", 0, 32_000_000, null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "annualIncome");
    // earnMin=0 일 때도 BETWEEN 으로 보냄. 위 케이스와 의도적으로 동일.
    assertThat(r.getOperator()).isEqualTo(RuleOperator.BETWEEN);
}

@Test
void annualIncome_returns_LTE_when_0043002_with_only_max_explicitly() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, "0043002", null, 32_000_000, null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "annualIncome");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.LTE);
    assertThat(r.getValue()).isEqualTo("32000000");
}

@Test
void annualIncome_returns_ANY_for_0043003_etc_with_earnEtcCn_in_sourceRef() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, "0043003", null, null,
                  "근로, 사업소득이 월 10만원 이상", null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "annualIncome");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
    assertThat(r.getSourceReference()).contains("근로, 사업소득이 월 10만원 이상");
}

@Test
void annualIncome_returns_ANY_when_null() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null, null, null, null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "annualIncome");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: 6개 신규 annualIncome FAIL (placeholder ANY는 1개만 실수로 PASS 가능 — 실제 의도와 일치하면 OK, 다음 step 에서 필드값 차이로 다시 FAIL)

- [ ] **Step 3: annualIncome 분기 구현**

placeholder line 교체:

```java
rules.add(buildAnnualIncomeRule(policyId, input));
```

```java
private EligibilityRule buildAnnualIncomeRule(Long policyId, CodeBasedExtractionInput input) {
    String code = input.earnConditionCd();
    if ("0043002".equals(code)) {
        Integer min = input.earnMin();
        Integer max = input.earnMax();
        boolean hasMin = min != null && min > 0;
        boolean hasMax = max != null && max > 0;
        String srcMinMax = "getPlcy.earnMinAmt: " + (min == null ? "0" : min)
                + ", earnMaxAmt: " + (max == null ? "0" : max);
        if (min != null && max != null && hasMax) {
            return rule(policyId, "annualIncome", "연소득", RuleOperator.BETWEEN,
                    min + "~" + max, srcMinMax);
        }
        if (hasMax) {
            return rule(policyId, "annualIncome", "연소득", RuleOperator.LTE,
                    String.valueOf(max), srcMinMax);
        }
        if (hasMin) {
            return rule(policyId, "annualIncome", "연소득", RuleOperator.GTE,
                    String.valueOf(min), srcMinMax);
        }
        return rule(policyId, "annualIncome", "연소득", RuleOperator.ANY, "ALL",
                "getPlcy.earnCndSeCd: 0043002 (수치 미지정)");
    }
    if ("0043003".equals(code)) {
        String etc = input.earnEtcCn();
        String trimmed = etc == null ? "" : etc.trim();
        String snippet = trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
        return rule(policyId, "annualIncome", "연소득", RuleOperator.ANY, "ALL",
                "getPlcy.earnEtcCn: " + (snippet.isEmpty() ? "(자유서술 없음)" : snippet));
    }
    // 0043001 무관 / null
    return rule(policyId, "annualIncome", "연소득", RuleOperator.ANY, "ALL",
            "getPlcy.earnCndSeCd: " + (code == null ? "미지정" : code));
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: annualIncome 6개 PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java
git commit -m "feat(be): CodeBasedRuleExtractor annualIncome 카테고리 구현 (TDD)"
```

---

### Task 6.5: employmentKind 카테고리

**Files:** (위 두 파일)

- [ ] **Step 1: employmentKind 4개 분기 실패 테스트 작성**

```java
@Test
void employmentKind_returns_ANY_for_0013010_no_limit() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, "0013010", null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "employmentKind");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
}

@Test
void employmentKind_returns_ANY_for_0013009_etc() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, "0013009", null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "employmentKind");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
}

@Test
void employmentKind_returns_EQ_EMPLOYEE_for_0013001() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, "0013001", null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "employmentKind");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.EQ);
    assertThat(r.getValue()).isEqualTo("EMPLOYEE");
}

@Test
void employmentKind_returns_EQ_for_each_known_code() {
    String[][] cases = {
            {"0013002","SELF_EMPLOYED"},
            {"0013003","UNEMPLOYED"},
            {"0013004","FREELANCER"},
            {"0013005","DAILY_WORKER"},
            {"0013006","ENTREPRENEUR"},
            {"0013007","PART_TIME"},
            {"0013008","FARMER"}
    };
    for (String[] c : cases) {
        List<EligibilityRule> rules = sut.extract(1L,
                input(null,null,null, null, null,null,null,null, c[0], null,null,null, List.of()));
        EligibilityRule r = findRule(rules, "employmentKind");
        assertThat(r.getOperator()).as(c[0]).isEqualTo(RuleOperator.EQ);
        assertThat(r.getValue()).as(c[0]).isEqualTo(c[1]);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: employmentKind 신규 4개 FAIL

- [ ] **Step 3: employmentKind 분기 구현**

placeholder line 교체 + 헬퍼 추가:

```java
rules.add(buildEmploymentKindRule(policyId, input));
```

```java
private static final java.util.Map<String, String> JOB_CODE_TO_ENUM = java.util.Map.of(
        "0013001", "EMPLOYEE",
        "0013002", "SELF_EMPLOYED",
        "0013003", "UNEMPLOYED",
        "0013004", "FREELANCER",
        "0013005", "DAILY_WORKER",
        "0013006", "ENTREPRENEUR",
        "0013007", "PART_TIME",
        "0013008", "FARMER"
);

private EligibilityRule buildEmploymentKindRule(Long policyId, CodeBasedExtractionInput input) {
    String code = input.employmentKindCd();
    String src = "getPlcy.jobCd: " + (code == null ? "미지정" : code);
    if (code == null || "0013009".equals(code) || "0013010".equals(code)) {
        return rule(policyId, "employmentKind", "취업상태", RuleOperator.ANY, "ALL", src);
    }
    String enumName = JOB_CODE_TO_ENUM.get(code);
    if (enumName == null) {
        return rule(policyId, "employmentKind", "취업상태", RuleOperator.ANY, "ALL", src + " (미인식)");
    }
    return rule(policyId, "employmentKind", "취업상태", RuleOperator.EQ, enumName, src);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: employmentKind 4개 PASS (모든 코드 enum 매핑 통과)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java
git commit -m "feat(be): CodeBasedRuleExtractor employmentKind 카테고리 구현 (TDD)"
```

---

### Task 6.6: education 카테고리

**Files:** (위 두 파일)

- [ ] **Step 1: education 분기 실패 테스트 작성**

```java
@Test
void education_returns_ANY_for_0049010_or_0049009_or_null() {
    for (String code : new String[]{"0049010", "0049009", null}) {
        List<EligibilityRule> rules = sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null, code,null,null, List.of()));
        EligibilityRule r = findRule(rules, "education");
        assertThat(r.getOperator()).as("code=" + code).isEqualTo(RuleOperator.ANY);
    }
}

@Test
void education_returns_EQ_for_each_known_code() {
    String[][] cases = {
            {"0049001","UNDER_HIGH"},
            {"0049002","HIGH_SCHOOL_IN"},
            {"0049003","HIGH_SCHOOL_EXPECTED"},
            {"0049004","HIGH_SCHOOL_GRAD"},
            {"0049005","COLLEGE_IN"},
            {"0049006","COLLEGE_EXPECTED"},
            {"0049007","COLLEGE_GRAD"},
            {"0049008","GRADUATE"}
    };
    for (String[] c : cases) {
        List<EligibilityRule> rules = sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null, c[0],null,null, List.of()));
        EligibilityRule r = findRule(rules, "education");
        assertThat(r.getOperator()).as(c[0]).isEqualTo(RuleOperator.EQ);
        assertThat(r.getValue()).as(c[0]).isEqualTo(c[1]);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: education 신규 2개 FAIL (코드 매핑 안 됨)

- [ ] **Step 3: education 분기 구현**

placeholder 교체:

```java
rules.add(buildEducationRule(policyId, input));
```

```java
private static final java.util.Map<String, String> SCHOOL_CODE_TO_ENUM = java.util.Map.ofEntries(
        java.util.Map.entry("0049001", "UNDER_HIGH"),
        java.util.Map.entry("0049002", "HIGH_SCHOOL_IN"),
        java.util.Map.entry("0049003", "HIGH_SCHOOL_EXPECTED"),
        java.util.Map.entry("0049004", "HIGH_SCHOOL_GRAD"),
        java.util.Map.entry("0049005", "COLLEGE_IN"),
        java.util.Map.entry("0049006", "COLLEGE_EXPECTED"),
        java.util.Map.entry("0049007", "COLLEGE_GRAD"),
        java.util.Map.entry("0049008", "GRADUATE")
);

private EligibilityRule buildEducationRule(Long policyId, CodeBasedExtractionInput input) {
    String code = input.educationCd();
    String src = "getPlcy.schoolCd: " + (code == null ? "미지정" : code);
    if (code == null || "0049009".equals(code) || "0049010".equals(code)) {
        return rule(policyId, "education", "학력", RuleOperator.ANY, "ALL", src);
    }
    String enumName = SCHOOL_CODE_TO_ENUM.get(code);
    if (enumName == null) {
        return rule(policyId, "education", "학력", RuleOperator.ANY, "ALL", src + " (미인식)");
    }
    return rule(policyId, "education", "학력", RuleOperator.EQ, enumName, src);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: education 2개 PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java
git commit -m "feat(be): CodeBasedRuleExtractor education 카테고리 구현 (TDD)"
```

---

### Task 6.7: majorField 카테고리

**Files:** (위 두 파일)

- [ ] **Step 1: majorField 분기 실패 테스트 작성**

```java
@Test
void majorField_returns_ANY_for_0011009_or_0011008_or_null() {
    for (String code : new String[]{"0011009", "0011008", null}) {
        List<EligibilityRule> rules = sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null, null, code,null, List.of()));
        EligibilityRule r = findRule(rules, "majorField");
        assertThat(r.getOperator()).as("code=" + code).isEqualTo(RuleOperator.ANY);
    }
}

@Test
void majorField_returns_EQ_for_each_known_code() {
    String[][] cases = {
            {"0011001","HUMANITIES"},
            {"0011002","SOCIAL"},
            {"0011003","ECONOMICS"},
            {"0011004","NATURAL"},
            {"0011005","ENGINEERING"},
            {"0011006","ARTS"},
            {"0011007","AGRICULTURE"}
    };
    for (String[] c : cases) {
        List<EligibilityRule> rules = sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null, null, c[0],null, List.of()));
        EligibilityRule r = findRule(rules, "majorField");
        assertThat(r.getOperator()).as(c[0]).isEqualTo(RuleOperator.EQ);
        assertThat(r.getValue()).as(c[0]).isEqualTo(c[1]);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: majorField 신규 2개 FAIL

- [ ] **Step 3: majorField 분기 구현**

placeholder 교체:

```java
rules.add(buildMajorFieldRule(policyId, input));
```

```java
private static final java.util.Map<String, String> MAJOR_CODE_TO_ENUM = java.util.Map.of(
        "0011001", "HUMANITIES",
        "0011002", "SOCIAL",
        "0011003", "ECONOMICS",
        "0011004", "NATURAL",
        "0011005", "ENGINEERING",
        "0011006", "ARTS",
        "0011007", "AGRICULTURE"
);

private EligibilityRule buildMajorFieldRule(Long policyId, CodeBasedExtractionInput input) {
    String code = input.majorFieldCd();
    String src = "getPlcy.plcyMajorCd: " + (code == null ? "미지정" : code);
    if (code == null || "0011008".equals(code) || "0011009".equals(code)) {
        return rule(policyId, "majorField", "전공", RuleOperator.ANY, "ALL", src);
    }
    String enumName = MAJOR_CODE_TO_ENUM.get(code);
    if (enumName == null) {
        return rule(policyId, "majorField", "전공", RuleOperator.ANY, "ALL", src + " (미인식)");
    }
    return rule(policyId, "majorField", "전공", RuleOperator.EQ, enumName, src);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: majorField 2개 PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java
git commit -m "feat(be): CodeBasedRuleExtractor majorField 카테고리 구현 (TDD)"
```

---

### Task 6.8: specializationField 카테고리

**Files:** (위 두 파일)

- [ ] **Step 1: specializationField 분기 실패 테스트 작성**

```java
@Test
void specializationField_returns_ANY_for_0014010_or_0014009_or_null() {
    for (String code : new String[]{"0014010", "0014009", null}) {
        List<EligibilityRule> rules = sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null, null, null, code, List.of()));
        EligibilityRule r = findRule(rules, "specializationField");
        assertThat(r.getOperator()).as("code=" + code).isEqualTo(RuleOperator.ANY);
    }
}

@Test
void specializationField_returns_EQ_for_each_known_code() {
    String[][] cases = {
            {"0014001","SME"},
            {"0014002","WOMAN"},
            {"0014003","BASIC_LIVELIHOOD"},
            {"0014004","SINGLE_PARENT"},
            {"0014005","DISABLED"},
            {"0014006","FARMER"},
            {"0014007","MILITARY"},
            {"0014008","LOCAL_TALENT"}
    };
    for (String[] c : cases) {
        List<EligibilityRule> rules = sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null, null, null, c[0], List.of()));
        EligibilityRule r = findRule(rules, "specializationField");
        assertThat(r.getOperator()).as(c[0]).isEqualTo(RuleOperator.EQ);
        assertThat(r.getValue()).as(c[0]).isEqualTo(c[1]);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: specializationField 신규 2개 FAIL

- [ ] **Step 3: specializationField 분기 구현**

placeholder 교체:

```java
rules.add(buildSpecializationRule(policyId, input));
```

```java
private static final java.util.Map<String, String> SBIZ_CODE_TO_ENUM = java.util.Map.ofEntries(
        java.util.Map.entry("0014001", "SME"),
        java.util.Map.entry("0014002", "WOMAN"),
        java.util.Map.entry("0014003", "BASIC_LIVELIHOOD"),
        java.util.Map.entry("0014004", "SINGLE_PARENT"),
        java.util.Map.entry("0014005", "DISABLED"),
        java.util.Map.entry("0014006", "FARMER"),
        java.util.Map.entry("0014007", "MILITARY"),
        java.util.Map.entry("0014008", "LOCAL_TALENT")
);

private EligibilityRule buildSpecializationRule(Long policyId, CodeBasedExtractionInput input) {
    String code = input.specializationCd();
    String src = "getPlcy.sbizCd: " + (code == null ? "미지정" : code);
    if (code == null || "0014009".equals(code) || "0014010".equals(code)) {
        return rule(policyId, "specializationField", "특화요건", RuleOperator.ANY, "ALL", src);
    }
    String enumName = SBIZ_CODE_TO_ENUM.get(code);
    if (enumName == null) {
        return rule(policyId, "specializationField", "특화요건", RuleOperator.ANY, "ALL", src + " (미인식)");
    }
    return rule(policyId, "specializationField", "특화요건", RuleOperator.EQ, enumName, src);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: specializationField 2개 PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java
git commit -m "feat(be): CodeBasedRuleExtractor specializationField 카테고리 구현 (TDD)"
```

---

### Task 6.9: region 카테고리

**Files:** (위 두 파일)

- [ ] **Step 1: region 분기 실패 테스트 작성**

```java
@Test
void region_returns_ANY_when_zipCodes_empty() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, null,null,null,null, List.of()));
    EligibilityRule r = findRule(rules, "region");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
    assertThat(r.getValue()).isEqualTo("ALL");
}

@Test
void region_returns_EQ_SEOUL_when_only_seoul_codes() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, null,null,null,null,
                  List.of("11000","11680","11710")));
    EligibilityRule r = findRule(rules, "region");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.EQ);
    assertThat(r.getValue()).isEqualTo("SEOUL");
}

@Test
void region_returns_IN_when_multiple_sido() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, null,null,null,null,
                  List.of("11680","26110")));
    EligibilityRule r = findRule(rules, "region");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.IN);
    assertThat(r.getValue().split(",")).containsExactlyInAnyOrder("SEOUL","BUSAN");
}

@Test
void region_returns_ANY_when_all_17_sido() {
    List<String> all17 = List.of(
        "11000","26000","27000","28000","29000","30000","31000","36000","41000",
        "51000","43000","44000","52000","46000","47000","48000","50000");
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, null,null,null,null, all17));
    EligibilityRule r = findRule(rules, "region");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
    assertThat(r.getValue()).isEqualTo("ALL");
}

@Test
void region_treats_legacy_prefix_42_as_GANGWON() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, null,null,null,null,
                  List.of("42000")));
    EligibilityRule r = findRule(rules, "region");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.EQ);
    assertThat(r.getValue()).isEqualTo("GANGWON");
}

@Test
void region_treats_legacy_prefix_45_as_JEONBUK() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(null,null,null, null, null,null,null,null, null,null,null,null,
                  List.of("45000")));
    EligibilityRule r = findRule(rules, "region");
    assertThat(r.getOperator()).isEqualTo(RuleOperator.EQ);
    assertThat(r.getValue()).isEqualTo("JEONBUK");
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: region 신규 6개 FAIL

- [ ] **Step 3: region 분기 구현**

placeholder 교체:

```java
rules.add(buildRegionRule(policyId, input));
```

```java
private static final java.util.Map<String, String> SIDO_PREFIX_TO_ENUM = java.util.Map.ofEntries(
        java.util.Map.entry("11", "SEOUL"),
        java.util.Map.entry("26", "BUSAN"),
        java.util.Map.entry("27", "DAEGU"),
        java.util.Map.entry("28", "INCHEON"),
        java.util.Map.entry("29", "GWANGJU"),
        java.util.Map.entry("30", "DAEJEON"),
        java.util.Map.entry("31", "ULSAN"),
        java.util.Map.entry("36", "SEJONG"),
        java.util.Map.entry("41", "GYEONGGI"),
        java.util.Map.entry("51", "GANGWON"),
        java.util.Map.entry("42", "GANGWON"),     // legacy
        java.util.Map.entry("43", "CHUNGBUK"),
        java.util.Map.entry("44", "CHUNGNAM"),
        java.util.Map.entry("52", "JEONBUK"),
        java.util.Map.entry("45", "JEONBUK"),     // legacy
        java.util.Map.entry("46", "JEONNAM"),
        java.util.Map.entry("47", "GYEONGBUK"),
        java.util.Map.entry("48", "GYEONGNAM"),
        java.util.Map.entry("50", "JEJU")
);

private static final int TOTAL_SIDO_COUNT = 17;

private EligibilityRule buildRegionRule(Long policyId, CodeBasedExtractionInput input) {
    java.util.List<String> codes = input.zipCodes();
    if (codes == null || codes.isEmpty()) {
        return rule(policyId, "region", "거주지", RuleOperator.ANY, "ALL",
                "getPlcy.zipCd: 미지정");
    }
    java.util.LinkedHashSet<String> sidos = new java.util.LinkedHashSet<>();
    for (String c : codes) {
        if (c == null || c.length() < 2) continue;
        String prefix = c.substring(0, 2);
        String e = SIDO_PREFIX_TO_ENUM.get(prefix);
        if (e != null) sidos.add(e);
    }
    String src = "getPlcy.zipCd: " + codes.size() + "개 코드 → 시도 " + sidos.size() + "개";
    if (sidos.isEmpty()) {
        return rule(policyId, "region", "거주지", RuleOperator.ANY, "ALL", src);
    }
    if (sidos.size() == 1) {
        String only = sidos.iterator().next();
        return rule(policyId, "region", "거주지", RuleOperator.EQ, only, src);
    }
    if (sidos.size() >= TOTAL_SIDO_COUNT) {
        return rule(policyId, "region", "거주지", RuleOperator.ANY, "ALL",
                src + " (전국)");
    }
    String csv = String.join(",", sidos);
    return rule(policyId, "region", "거주지", RuleOperator.IN, csv, src);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: region 6개 + 기존 모두 PASS

- [ ] **Step 5: 8개 카테고리 통합 회귀 테스트 추가**

```java
@Test
void extract_returns_8_distinct_fields_with_correct_order() {
    List<EligibilityRule> rules = sut.extract(1L,
            input(19, 34, "Y", "0055002", "0043002", null, 32_000_000, null,
                  "0013001", "0049007", "0011005", "0014001", List.of("11680")));
    assertThat(rules).extracting(EligibilityRule::getField)
            .containsExactly("age", "maritalStatus", "annualIncome", "employmentKind",
                             "education", "majorField", "specializationField", "region");
}
```

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractorTest -i`
Expected: 신규 회귀 PASS

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractor.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/CodeBasedRuleExtractorTest.java
git commit -m "feat(be): CodeBasedRuleExtractor region 카테고리 구현 + 8 카테고리 회귀 테스트 (TDD)"
```

---

## Phase 7 — 룰 추출 wiring

### Task 7.1: CodeBasedRuleExtractionService (DELETE+INSERT 오케스트레이션)

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/application/service/CodeBasedRuleExtractionService.java`
- Create: `backend/src/test/java/com/youthfit/eligibility/application/service/CodeBasedRuleExtractionServiceTest.java`

- [ ] **Step 1: CodeBasedRuleExtractionService 작성**

```java
package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.eligibility.domain.service.CodeBasedRuleExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CodeBasedRuleExtractionService {

    private final CodeBasedRuleExtractor extractor;
    private final EligibilityRuleRepository ruleRepository;

    public void extractAndPersist(Long policyId, CodeBasedExtractionInput input) {
        List<EligibilityRule> rules = extractor.extract(policyId, input);
        ruleRepository.deleteAllByPolicyId(policyId);
        ruleRepository.saveAll(rules);
    }
}
```

- [ ] **Step 2: 단위 테스트 작성**

```java
package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.eligibility.domain.service.CodeBasedRuleExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class CodeBasedRuleExtractionServiceTest {

    private final CodeBasedRuleExtractor extractor = mock(CodeBasedRuleExtractor.class);
    private final EligibilityRuleRepository repository = mock(EligibilityRuleRepository.class);
    private final CodeBasedRuleExtractionService sut = new CodeBasedRuleExtractionService(extractor, repository);

    @Test
    void extractAndPersist_deletes_existing_then_saves_extracted_rules() {
        CodeBasedExtractionInput input = new CodeBasedExtractionInput(
                null,null,null, null, null,null,null,null,
                null,null,null,null, List.of());
        EligibilityRule r1 = mock(EligibilityRule.class);
        EligibilityRule r2 = mock(EligibilityRule.class);
        given(extractor.extract(99L, input)).willReturn(List.of(r1, r2));

        sut.extractAndPersist(99L, input);

        var inOrder = inOrder(repository);
        inOrder.verify(repository).deleteAllByPolicyId(99L);
        @SuppressWarnings("unchecked")
        var captor = forClass(List.class);
        inOrder.verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(r1, r2);
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests CodeBasedRuleExtractionServiceTest -i`
Expected: 컴파일 OK이면 PASS, 아니면 mock 설정 따라 PASS여야 함. 실제로는 Step 1 구현이 이미 됐으므로 PASS.

(만약 PASS면 — TDD 원칙에 어긋나지만 — 이 task는 wiring 외 새 로직이 없어 무방. 다음 task로 진행)

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/service/CodeBasedRuleExtractionService.java \
        backend/src/test/java/com/youthfit/eligibility/application/service/CodeBasedRuleExtractionServiceTest.java
git commit -m "feat(be): CodeBasedRuleExtractionService 신규 (DELETE+INSERT 오케스트레이션)"
```

---

### Task 7.2: IngestionService에서 동기 추출기 호출

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`
- Modify: `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java`

- [ ] **Step 1: rawCodes 분기 실패 테스트 작성**

`IngestionServiceTest`에 추가:

```java
@Test
void receivePolicy_invokes_codeBased_extractor_when_rawCodes_present_and_REGISTERED() {
    IngestPolicyCommand command = sampleCommandWithRawCodes();
    given(policyIngestionService.registerPolicy(any()))
            .willReturn(PolicyIngestionResult.registered(123L));

    sut.receivePolicy(command);

    var captor = ArgumentCaptor.forClass(CodeBasedExtractionInput.class);
    verify(codeBasedRuleExtractionService).extractAndPersist(eq(123L), captor.capture());
    assertThat(captor.getValue().maritalStatusCd()).isEqualTo("0055002");
    assertThat(captor.getValue().zipCodes()).containsExactly("11680");
}

@Test
void receivePolicy_invokes_codeBased_extractor_when_rawCodes_present_and_UPDATED() {
    IngestPolicyCommand command = sampleCommandWithRawCodes();
    given(policyIngestionService.registerPolicy(any()))
            .willReturn(PolicyIngestionResult.updated(456L));

    sut.receivePolicy(command);

    verify(codeBasedRuleExtractionService).extractAndPersist(eq(456L), any());
}

@Test
void receivePolicy_skips_codeBased_extractor_when_SKIPPED_DUPLICATE() {
    IngestPolicyCommand command = sampleCommandWithRawCodes();
    given(policyIngestionService.registerPolicy(any()))
            .willReturn(PolicyIngestionResult.skippedDuplicate(789L));

    sut.receivePolicy(command);

    verify(codeBasedRuleExtractionService, never()).extractAndPersist(any(), any());
}

@Test
void receivePolicy_skips_codeBased_extractor_when_rawCodes_null() {
    IngestPolicyCommand command = sampleCommandWithDetailFields();  // rawCodes=null
    given(policyIngestionService.registerPolicy(any()))
            .willReturn(PolicyIngestionResult.registered(111L));

    sut.receivePolicy(command);

    verify(codeBasedRuleExtractionService, never()).extractAndPersist(any(), any());
}

private IngestPolicyCommand sampleCommandWithRawCodes() {
    return new IngestPolicyCommand(
            "https://src.kr", "YOUTH_CENTER", LocalDateTime.now(),
            "EXT-2", "제목", "요약", "[지원대상]\n내용", "복지", "서울특별시",
            null, null, 2026, null, "보조금",
            "기관", "연락처",
            List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(),
            null, null, null, null, null,
            null, null, null,
            null, null, null,
            new IngestPolicyCommand.RawCodes(
                    19, 34, "Y",
                    "0055002", "0043001", 0, 0, null,
                    "0013001", "0049007", "0011005", "0014001",
                    List.of("11680"))
    );
}
```

상단 import 추가:
```java
import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.application.service.CodeBasedRuleExtractionService;
```

테스트 클래스에 mock 필드 추가 (다른 mock 위치에):
```java
@Mock CodeBasedRuleExtractionService codeBasedRuleExtractionService;
```

그리고 `sut` 생성자 인자에도 추가 (다음 step에서 IngestionService에 의존성 추가하면서 일치).

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests IngestionServiceTest -i`
Expected: 컴파일 에러 (`IngestionService` 생성자 인자 불일치, `codeBasedRuleExtractionService` 필드 없음)

- [ ] **Step 3: IngestionService에 의존성 추가**

`IngestionService.java` 필드에 추가:

```java
private final CodeBasedRuleExtractionService codeBasedRuleExtractionService;
```

상단 import:
```java
import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.application.service.CodeBasedRuleExtractionService;
```

- [ ] **Step 4: receivePolicy()에서 동기 호출 분기 추가**

`SKIPPED_DUPLICATE` early return 다음, `eventPublisher.publishEvent` 직전에 삽입:

```java
PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
duplicate = ingestionResult.outcome() != Outcome.REGISTERED;

if (ingestionResult.outcome() == Outcome.SKIPPED_DUPLICATE) {
    return new IngestPolicyResult(UUID.randomUUID(), "SKIPPED_DUPLICATE");
}

// ── deterministic 룰 추출 (rawCodes 있을 때만) ──
if (command.rawCodes() != null) {
    CodeBasedExtractionInput extractionInput = new CodeBasedExtractionInput(
            command.rawCodes().ageMin(),
            command.rawCodes().ageMax(),
            command.rawCodes().ageLimitYn(),
            command.rawCodes().maritalStatusCd(),
            command.rawCodes().earnConditionCd(),
            command.rawCodes().earnMin(),
            command.rawCodes().earnMax(),
            command.rawCodes().earnEtcCn(),
            command.rawCodes().employmentKindCd(),
            command.rawCodes().educationCd(),
            command.rawCodes().majorFieldCd(),
            command.rawCodes().specializationCd(),
            command.rawCodes().zipCodes() == null ? List.of() : command.rawCodes().zipCodes()
    );
    codeBasedRuleExtractionService.extractAndPersist(ingestionResult.policyId(), extractionInput);
}

eventPublisher.publishEvent(new PolicyUpsertedEvent(ingestionResult.policyId(), command.title()));
triggerAttachmentDownload(ingestionResult.policyId());
return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests IngestionServiceTest -i`
Expected: 신규 4개 + 기존 모두 PASS

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java
git commit -m "feat(be): IngestionService에서 rawCodes 있을 때 deterministic 룰 동기 추출"
```

---

### Task 7.3: EligibilityRuleGenerationEventListener에 code-v1 가드

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListener.java`
- Modify: `backend/src/test/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListenerTest.java`

- [ ] **Step 1: 가드 분기 실패 테스트 작성**

`EligibilityRuleGenerationEventListenerTest`에 추가:

```java
@Test
void onPolicyUpserted_skips_LLM_when_codeV1_rules_exist() {
    EligibilityRule codeRule = mock(EligibilityRule.class);
    given(codeRule.getExtractionVersion()).willReturn("code-v1");
    given(ruleRepository.findAllByPolicyId(99L)).willReturn(List.of(codeRule));

    sut.onPolicyUpserted(new PolicyUpsertedEvent(99L, "title"));

    verify(eligibilityRuleGenerationService, never()).generateRules(any());
}

@Test
void onPolicyUpserted_invokes_LLM_when_no_codeV1_rules() {
    EligibilityRule llmRule = mock(EligibilityRule.class);
    given(llmRule.getExtractionVersion()).willReturn("v1");
    given(ruleRepository.findAllByPolicyId(99L)).willReturn(List.of(llmRule));

    sut.onPolicyUpserted(new PolicyUpsertedEvent(99L, "title"));

    verify(eligibilityRuleGenerationService).generateRules(any());
}

@Test
void onPolicyUpserted_invokes_LLM_when_no_existing_rules() {
    given(ruleRepository.findAllByPolicyId(99L)).willReturn(List.of());

    sut.onPolicyUpserted(new PolicyUpsertedEvent(99L, "title"));

    verify(eligibilityRuleGenerationService).generateRules(any());
}
```

상단 import + mock 필드:
```java
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
// ...
@Mock EligibilityRuleRepository ruleRepository;
```

테스트의 `sut` 생성도 `new EligibilityRuleGenerationEventListener(eligibilityRuleGenerationService, ruleRepository)` 로 변경.

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests EligibilityRuleGenerationEventListenerTest -i`
Expected: 컴파일 에러 (생성자 인자 불일치)

- [ ] **Step 3: 리스너에 ruleRepository 주입 + 가드 추가**

`EligibilityRuleGenerationEventListener.java`:

```java
package com.youthfit.eligibility.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EligibilityRuleGenerationEventListener {

    private static final Logger log = LoggerFactory.getLogger(EligibilityRuleGenerationEventListener.class);
    private static final String CODE_BASED_VERSION = "code-v1";

    private final EligibilityRuleGenerationService eligibilityRuleGenerationService;
    private final EligibilityRuleRepository ruleRepository;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyUpserted(PolicyUpsertedEvent event) {
        if (hasCodeBasedRules(event.policyId())) {
            log.info("deterministic 룰 존재, LLM 추출 스킵: policyId={}", event.policyId());
            return;
        }
        try {
            eligibilityRuleGenerationService.generateRules(
                    new GenerateEligibilityRulesCommand(event.policyId()));
        } catch (Exception e) {
            log.warn("적합도 룰 추출 실패 (event=PolicyUpsertedEvent): policyId={}", event.policyId(), e);
        }
    }

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAttachmentReindexed(PolicyAttachmentReindexedEvent event) {
        if (hasCodeBasedRules(event.policyId())) {
            log.info("deterministic 룰 존재, LLM 재추출 스킵: policyId={}", event.policyId());
            return;
        }
        try {
            eligibilityRuleGenerationService.generateRules(
                    new GenerateEligibilityRulesCommand(event.policyId()));
        } catch (Exception e) {
            log.warn("적합도 룰 재추출 실패 (event=PolicyAttachmentReindexedEvent): policyId={}", event.policyId(), e);
        }
    }

    private boolean hasCodeBasedRules(Long policyId) {
        return ruleRepository.findAllByPolicyId(policyId).stream()
                .anyMatch(r -> CODE_BASED_VERSION.equals(r.getExtractionVersion()));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests EligibilityRuleGenerationEventListenerTest -i`
Expected: 신규 3개 + 기존 모두 PASS

- [ ] **Step 5: 전체 백엔드 빌드 통과 확인**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL (모든 테스트 통과)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListener.java \
        backend/src/test/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListenerTest.java
git commit -m "feat(be): listener에 code-v1 가드 추가 — deterministic 룰 있으면 LLM 호출 스킵"
```

---

## Phase 8 — 워크플로우 transform

### Task 8.1: youth-center-seoul.json transform 노드 jsCode 갱신

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`

- [ ] **Step 1: transform 노드의 jsCode 교체**

`정책 → IngestPolicyRequest 변환` 노드의 `parameters.jsCode` 전체를 다음으로 교체:

```js
// === 코드 사전 (data.go.kr 공식 엑셀 출처) — body 합성 전용 ===
const CODE = {
  mrgSttsCd: { '0055001':'기혼', '0055002':'미혼', '0055003':'제한없음' },
  earnCndSeCd: { '0043001':'무관', '0043002':'연소득', '0043003':'기타' },
  jobCd: { '0013001':'재직자','0013002':'자영업자','0013003':'미취업자','0013004':'프리랜서','0013005':'일용근로자','0013006':'(예비)창업자','0013007':'단기근로자','0013008':'영농종사자','0013009':'기타','0013010':'제한없음' },
  schoolCd: { '0049001':'고졸 미만','0049002':'고교 재학','0049003':'고졸 예정','0049004':'고교 졸업','0049005':'대학 재학','0049006':'대졸 예정','0049007':'대학 졸업','0049008':'석·박사','0049009':'기타','0049010':'제한없음' },
  plcyMajorCd: { '0011001':'인문계열','0011002':'사회계열','0011003':'상경계열','0011004':'이학계열','0011005':'공학계열','0011006':'예체능계열','0011007':'농산업계열','0011008':'기타','0011009':'제한없음' },
  sbizCd: { '0014001':'중소기업','0014002':'여성','0014003':'기초생활수급자','0014004':'한부모가정','0014005':'장애인','0014006':'농업인','0014007':'군인','0014008':'지역인재','0014009':'기타','0014010':'제한없음' },
  plcyPvsnMthdCd: { '0042001':'인프라 구축','0042002':'프로그램','0042003':'직접대출','0042004':'공공기관','0042005':'계약(위탁운영)','0042006':'보조금','0042007':'대출보증','0042008':'공적보험','0042009':'조세지출','0042010':'바우처','0042011':'정보제공','0042012':'경제적 규제','0042013':'기타' },
  bizPrdSeCd: { '0056001':'특정기간','0056002':'기타' },
  aplyPrdSeCd: { '0057001':'특정기간','0057002':'상시','0057003':'마감' }
};

const GU = { '11110':'종로구','11140':'중구','11170':'용산구','11200':'성동구','11215':'광진구','11230':'동대문구','11260':'중랑구','11290':'성북구','11305':'강북구','11320':'도봉구','11350':'노원구','11380':'은평구','11410':'서대문구','11440':'마포구','11470':'양천구','11500':'강서구','11530':'구로구','11545':'금천구','11560':'영등포구','11590':'동작구','11620':'관악구','11650':'서초구','11680':'강남구','11710':'송파구','11740':'강동구' };
const SEOUL_CODES = new Set(Object.keys(GU).concat(['11000']));

// === 헬퍼 ===
function clean(s) {
  if (s == null) return '';
  return String(s)
    .replace(/ᬼ/g, '·')
    .replace(/ /g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}
function splitTokens(s, sep) {
  if (!s) return [];
  return clean(s).split(sep || /[,・･·]/).map(x => x.trim()).filter(Boolean);
}
function parseIntOrNull(s) {
  if (s == null || String(s).trim() === '') return null;
  const n = parseInt(String(s).trim(), 10);
  if (isNaN(n) || n === 0) return null;
  return n;
}
function parseYmd(s) {
  if (!s) return null;
  const c = String(s).trim();
  if (c.length !== 8) return null;
  return `${c.slice(0,4)}-${c.slice(4,6)}-${c.slice(6,8)}`;
}

// === lclsfNm → Category (한글) ===
function mapCategory(lclsf) {
  const tokens = splitTokens(lclsf);
  const order = [
    [['일자리'], '일자리'],
    [['주거'], '주거'],
    [['교육','직업훈련'], '교육'],
    [['금융'], '금융'],
    [['문화','여가'], '문화'],
    [['복지','복지문화'], '복지'],
    [['참여','권리','참여권리'], '참여']
  ];
  for (const [keys, label] of order) {
    for (const t of tokens) {
      if (keys.some(k => t.includes(k))) return label;
    }
  }
  return '복지';
}

function parseApplyPeriod(aplyYmd) {
  if (!aplyYmd) return { start: null, end: null };
  const m = String(aplyYmd).match(/(\d{8})\s*~\s*(\d{8})/);
  if (!m) return { start: null, end: null };
  function fmt(s) { return `${s.slice(0,4)}-${s.slice(4,6)}-${s.slice(6,8)}`; }
  return { start: fmt(m[1]), end: fmt(m[2]) };
}

function regionLabel(zipCd) {
  const codes = splitTokens(zipCd, ',');
  const seoul = codes.filter(c => SEOUL_CODES.has(c));
  const guCount = seoul.filter(c => c !== '11000').length;
  if (guCount >= 25) return '서울특별시';
  if (guCount === 1) {
    const guCode = seoul.find(c => c !== '11000');
    return `서울특별시 ${GU[guCode] || ''}`.trim();
  }
  return '서울특별시';
}

// === 본문 섹션 결합 ===
function buildBody(p) {
  const lines = [];
  if (clean(p.plcyExplnCn)) lines.push('[개요]', clean(p.plcyExplnCn), '');

  const tgt = [];
  const minA = parseInt(p.sprtTrgtMinAge || '0', 10);
  const maxA = parseInt(p.sprtTrgtMaxAge || '0', 10);
  if (p.sprtTrgtAgeLmtYn === 'N') tgt.push('- 연령: 제한없음');
  else if (minA || maxA) tgt.push(`- 연령: ${minA}~${maxA}세`);
  if (CODE.mrgSttsCd[p.mrgSttsCd]) tgt.push(`- 결혼상태: ${CODE.mrgSttsCd[p.mrgSttsCd]}`);
  if (CODE.earnCndSeCd[p.earnCndSeCd]) {
    let earn = `- 소득조건: ${CODE.earnCndSeCd[p.earnCndSeCd]}`;
    const eMin = parseInt(p.earnMinAmt || '0', 10);
    const eMax = parseInt(p.earnMaxAmt || '0', 10);
    if (eMin || eMax) earn += ` (${eMin.toLocaleString()}~${eMax.toLocaleString()}원)`;
    if (clean(p.earnEtcCn) && clean(p.earnEtcCn) !== '-') earn += ` ${clean(p.earnEtcCn)}`;
    tgt.push(earn);
  }
  if (CODE.jobCd[p.jobCd]) tgt.push(`- 취업상태: ${CODE.jobCd[p.jobCd]}`);
  if (CODE.schoolCd[p.schoolCd]) tgt.push(`- 학력: ${CODE.schoolCd[p.schoolCd]}`);
  if (CODE.plcyMajorCd[p.plcyMajorCd]) tgt.push(`- 전공: ${CODE.plcyMajorCd[p.plcyMajorCd]}`);
  if (CODE.sbizCd[p.sbizCd]) tgt.push(`- 특화요건: ${CODE.sbizCd[p.sbizCd]}`);
  if (clean(p.ptcpPrpTrgtCn)) tgt.push(`- 참여 제한 대상: ${clean(p.ptcpPrpTrgtCn)}`);
  if (tgt.length) { lines.push('[지원대상]'); lines.push(...tgt); lines.push(''); }

  const sel = [];
  if (clean(p.srngMthdCn)) sel.push(clean(p.srngMthdCn));
  if (clean(p.addAplyQlfcCndCn) && clean(p.addAplyQlfcCndCn) !== '해당사항 없음') sel.push(`추가 자격: ${clean(p.addAplyQlfcCndCn)}`);
  if (sel.length) { lines.push('[선정기준]'); lines.push(...sel); lines.push(''); }
  else { lines.push('[선정기준]', '별도 문의', ''); }

  const sup = [];
  if (clean(p.plcySprtCn)) sup.push(clean(p.plcySprtCn));
  if (CODE.plcyPvsnMthdCd[p.plcyPvsnMthdCd]) sup.push(`제공방식: ${CODE.plcyPvsnMthdCd[p.plcyPvsnMthdCd]}`);
  const sclCnt = parseInt(p.sprtSclCnt || '0', 10);
  if (sclCnt) {
    const arvl = p.sprtArvlSeqYn === 'Y' ? ' (선착순)' : '';
    sup.push(`지원규모: ${sclCnt.toLocaleString()}명${arvl}`);
  }
  if (sup.length) { lines.push('[지원내용]'); lines.push(...sup); lines.push(''); }

  if (clean(p.sbmsnDcmntCn)) lines.push('[제출서류]', clean(p.sbmsnDcmntCn), '');

  const bizStart = clean(p.bizPrdBgngYmd);
  const bizEnd = clean(p.bizPrdEndYmd);
  if (bizStart && bizEnd) {
    function fmt(s) { return s.length === 8 ? `${s.slice(0,4)}-${s.slice(4,6)}-${s.slice(6,8)}` : s; }
    let line = `${fmt(bizStart)} ~ ${fmt(bizEnd)}`;
    if (CODE.bizPrdSeCd[p.bizPrdSeCd]) line += ` (${CODE.bizPrdSeCd[p.bizPrdSeCd]})`;
    lines.push('[사업기간]', line);
    if (clean(p.bizPrdEtcCn)) lines.push(clean(p.bizPrdEtcCn));
    lines.push('');
  }

  if (clean(p.etcMttrCn)) lines.push('[기타]', clean(p.etcMttrCn));

  return lines.join('\n').trim();
}

// === 메인 ===
const p = $input.first().json;
if (p._empty) return [{ json: { _empty: true } }];

const { start, end } = parseApplyPeriod(p.aplyYmd);
const category = mapCategory(p.lclsfNm);
const region = regionLabel(p.zipCd);

const themeTags = Array.from(new Set([
  ...splitTokens(p.lclsfNm),
  ...splitTokens(p.mclsfNm),
  ...splitTokens(p.plcyKywdNm)
].map(clean).filter(Boolean)));

const targetTags = [];
const sbiz = CODE.sbizCd[p.sbizCd];
if (sbiz && sbiz !== '제한없음' && sbiz !== '기타') targetTags.push(sbiz);

const orgParts = [];
if (clean(p.sprvsnInstCdNm)) orgParts.push(clean(p.sprvsnInstCdNm));
if (clean(p.operInstCdNm) && clean(p.operInstCdNm) !== clean(p.sprvsnInstCdNm)) orgParts.push(clean(p.operInstCdNm));
let organization = orgParts.join(' / ');
if (organization.length > 200) organization = organization.slice(0, 200);

const contact = clean(p.sprvsnInstPicNm) ? `담당: ${clean(p.sprvsnInstPicNm)}` : '';

// referenceSites: aplyUrlAddr는 별도 applyUrl로 분리됨
const referenceSites = [];
if (clean(p.refUrlAddr1)) referenceSites.push({ name: '참고 사이트', url: clean(p.refUrlAddr1) });
if (clean(p.refUrlAddr2)) referenceSites.push({ name: '참고 사이트', url: clean(p.refUrlAddr2) });

const applyMethods = [];
if (clean(p.plcyAplyMthdCn)) applyMethods.push({ stageName: '신청 절차', description: clean(p.plcyAplyMthdCn) });

const referenceYear = (p.frstRegDt && p.frstRegDt.length >= 4) ? parseInt(p.frstRegDt.slice(0, 4), 10) : null;

const result = {
  source: {
    url: `https://www.youthcenter.go.kr/youngPlcyUnif/youngPlcyUnifDtl.do?plcyNo=${p.plcyNo}`,
    type: 'YOUTH_CENTER',
    fetchedAt: new Date().toISOString().replace('Z', '')
  },
  rawData: {
    externalId: p.plcyNo,
    title: clean(p.plcyNm) || `(정책 ${p.plcyNo})`,
    summary: clean(p.plcyExplnCn) || clean(p.plcyNm),
    body: buildBody(p),
    category,
    region,
    applyStart: start,
    applyEnd: end,
    referenceYear,
    supportCycle: null,
    provideType: CODE.plcyPvsnMthdCd[p.plcyPvsnMthdCd] || null,
    organization,
    contact,
    lifeTags: ['청년'],
    themeTags,
    targetTags,
    attachments: [],
    referenceSites,
    applyMethods,

    // ── 신규 텍스트 11개 ──
    screeningMethod: clean(p.srngMthdCn) || null,
    submissionDocuments: clean(p.sbmsnDcmntCn) || null,
    additionalQualification: clean(p.addAplyQlfcCndCn) || null,
    participationRestriction: clean(p.ptcpPrpTrgtCn) || null,
    additionalNotes: clean(p.etcMttrCn) || null,
    businessPeriodStart: parseYmd(p.bizPrdBgngYmd),
    businessPeriodEnd: parseYmd(p.bizPrdEndYmd),
    businessPeriodNote: clean(p.bizPrdEtcCn) || null,
    supportScale: parseIntOrNull(p.sprtSclCnt),
    firstComeFirstServed: p.sprtArvlSeqYn === 'Y',
    applyUrl: clean(p.aplyUrlAddr) || null,

    // ── 신규 rawCodes ──
    rawCodes: {
      ageMin: parseIntOrNull(p.sprtTrgtMinAge),
      ageMax: parseIntOrNull(p.sprtTrgtMaxAge),
      ageLimitYn: p.sprtTrgtAgeLmtYn || null,
      maritalStatusCd: p.mrgSttsCd || null,
      earnConditionCd: p.earnCndSeCd || null,
      earnMin: parseIntOrNull(p.earnMinAmt),
      earnMax: parseIntOrNull(p.earnMaxAmt),
      earnEtcCn: clean(p.earnEtcCn) || null,
      employmentKindCd: p.jobCd || null,
      educationCd: p.schoolCd || null,
      majorFieldCd: p.plcyMajorCd || null,
      specializationCd: p.sbizCd || null,
      zipCodes: splitTokens(p.zipCd, ',')
    }
  }
};
return [{ json: result }];
```

(jsCode는 JSON 파일에 string으로 들어가므로 `\n` 이스케이프 필요. 가장 안전한 방법은: 위 JS를 별도 파일에 작성 후 `node -e` 또는 jq 로 인라인 처리. 또는 n8n UI에서 직접 붙여넣고 파일 export.)

실용적 절차:
```bash
# 위 JS 코드를 /tmp/transform.js 에 저장한 뒤
node -e 'const fs=require("fs"); const js=fs.readFileSync("/tmp/transform.js","utf8"); const wf=JSON.parse(fs.readFileSync("n8n/workflows/youth-center-seoul.json","utf8")); const node=wf.nodes.find(n=>n.name==="정책 → IngestPolicyRequest 변환"); node.parameters.jsCode=js; fs.writeFileSync("n8n/workflows/youth-center-seoul.json", JSON.stringify(wf,null,2));'
```

- [ ] **Step 2: JSON 유효성 확인**

Run: `python3 -m json.tool n8n/workflows/youth-center-seoul.json > /dev/null`
Expected: 에러 없음

- [ ] **Step 3: lastPage=1 (test mode) 유지 확인**

Run: `grep -n 'lastPage' n8n/workflows/youth-center-seoul.json`
Expected: `const lastPage = 1;` 라인이 살아 있음 (test mode)

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/youth-center-seoul.json
git commit -m "feat(n8n): 온통청년 transform에 정책 상세 11개 + rawCodes 출력 추가"
```

---

## Phase 9 — 프론트엔드

### Task 9.1: PolicyDetail TypeScript 타입 확장

**Files:**
- Modify: `frontend/src/types/policy.ts`

- [ ] **Step 1: PolicyDetail interface에 11개 필드 추가**

`PolicyDetail`에 다음 필드 추가:

```ts
export interface PolicyDetail extends Policy {
  body: string | null;
  supportTarget: string | null;
  selectionCriteria: string | null;
  supportContent: string | null;
  contact: string | null;
  referenceYear: number | null;
  supportCycle: string | null;
  provideType: string | null;
  // ── 신규 ──
  screeningMethod: string | null;
  submissionDocuments: string | null;
  additionalQualification: string | null;
  participationRestriction: string | null;
  additionalNotes: string | null;
  businessPeriodStart: string | null;
  businessPeriodEnd: string | null;
  businessPeriodNote: string | null;
  supportScale: number | null;
  firstComeFirstServed: boolean;
  applyUrl: string | null;
  // ── 기존 ──
  lifeTags: string[];
  themeTags: string[];
  targetTags: string[];
  attachments: PolicyAttachment[];
  referenceSites: PolicyReferenceSite[];
  applyMethods: PolicyApplyMethod[];
  sourceUrl: string | null;
  createdAt: string;
  updatedAt: string;
}
```

- [ ] **Step 2: TypeScript 빌드 확인**

Run: `cd frontend && npx tsc --noEmit`
Expected: 컴파일 에러 (`PolicyDetailPage.tsx` 등 사용처에서 신규 필드 사용 안 해도 OK, but 신규 추가는 컴파일 영향 없음)

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/types/policy.ts
git commit -m "feat(fe): PolicyDetail 타입에 정책 상세 11개 필드 추가"
```

---

### Task 9.2: PolicyDetailPage에 신규 카드 섹션 + applyUrl CTA 추가

**Files:**
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: 현재 PolicyDetailPage 구조 확인**

Run: `head -50 frontend/src/pages/PolicyDetailPage.tsx`
구조 파악: 어떤 위치에 카드들이 렌더되는지 확인.

- [ ] **Step 2: 신규 카드 컴포넌트 인라인으로 추가**

`PolicyDetailPage.tsx`의 메인 렌더 트리에 다음 섹션들을 추가. (배치는 spec 9.1 다이어그램 참조 — summary 다음에 CTA, 그 다음에 사업기간/지원규모, 그 다음에 기존 [지원대상] 후 [추가자격조건]/[참여제한대상], 기존 [선정기준] 후 [심사방법], 기존 [지원내용] 후 [제출서류]/[기타사항] 순)

```tsx
{/* applyUrl CTA — summary 다음에 */}
{policy.applyUrl && (
  <a
    href={policy.applyUrl}
    target="_blank"
    rel="noopener noreferrer"
    className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-500 px-6 py-3 text-white font-semibold hover:bg-blue-600 transition-colors"
  >
    공식 신청 페이지로 이동
    <span aria-hidden>→</span>
  </a>
)}

{/* 사업기간 / 지원규모 — chip 한 줄 */}
{(policy.businessPeriodStart || policy.businessPeriodNote || policy.supportScale != null) && (
  <div className="flex flex-wrap gap-2 text-sm text-gray-700">
    {policy.businessPeriodStart && policy.businessPeriodEnd && (
      <span className="rounded-full bg-gray-100 px-3 py-1">
        사업기간: {policy.businessPeriodStart} ~ {policy.businessPeriodEnd}
        {policy.businessPeriodNote && ` (${policy.businessPeriodNote})`}
      </span>
    )}
    {policy.supportScale != null && (
      <span className="rounded-full bg-gray-100 px-3 py-1">
        지원규모: {policy.supportScale.toLocaleString()}명
        {policy.firstComeFirstServed && ' · 선착순'}
      </span>
    )}
  </div>
)}

{/* 추가 자격조건 — 지원대상 카드 다음 */}
{policy.additionalQualification && (
  <section className="rounded-lg border border-gray-200 p-4">
    <h3 className="mb-2 font-semibold">추가 자격조건</h3>
    <p className="whitespace-pre-line text-sm text-gray-700">{policy.additionalQualification}</p>
  </section>
)}

{/* 참여 제한 대상 — 경고 톤 */}
{policy.participationRestriction && (
  <section className="rounded-lg border border-amber-200 bg-amber-50 p-4">
    <h3 className="mb-2 font-semibold text-amber-900">⚠ 참여 제한 대상</h3>
    <p className="whitespace-pre-line text-sm text-amber-900">{policy.participationRestriction}</p>
  </section>
)}

{/* 심사방법 — 선정기준 카드 다음 */}
{policy.screeningMethod && (
  <section className="rounded-lg border border-gray-200 p-4">
    <h3 className="mb-2 font-semibold">심사방법</h3>
    <p className="whitespace-pre-line text-sm text-gray-700">{policy.screeningMethod}</p>
  </section>
)}

{/* 제출서류 — 지원내용 카드 다음 */}
{policy.submissionDocuments && (
  <section className="rounded-lg border border-gray-200 p-4">
    <h3 className="mb-2 font-semibold">제출서류</h3>
    <ul className="list-disc pl-5 text-sm text-gray-700 whitespace-pre-line">
      {policy.submissionDocuments}
    </ul>
  </section>
)}

{/* 기타사항 — 보조 톤 */}
{policy.additionalNotes && (
  <section className="rounded-lg border border-gray-100 bg-gray-50 p-4">
    <h3 className="mb-2 text-sm font-semibold text-gray-600">기타사항</h3>
    <p className="whitespace-pre-line text-sm italic text-gray-600">{policy.additionalNotes}</p>
  </section>
)}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd frontend && npm run build`
Expected: 성공

- [ ] **Step 4: 로컬 dev 서버에서 BOKJIRO 정책 상세 한 건 확인 (회귀)**

Run: `cd frontend && npm run dev` 후 브라우저로 BOKJIRO 정책 상세 페이지 열기.
Expected: 신규 카드 모두 숨김 (모든 신규 필드 NULL이라). 기존 모양 그대로.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(fe): 정책 상세 페이지에 카드 섹션 6개 + applyUrl CTA 추가"
```

---

### Task 9.3: 적합도 결과 화면에서 ANY 룰 ✅ 표시

**Files:**
- (탐색 후 결정) 적합도 결과 카드 컴포넌트 — 추정 경로: `frontend/src/components/eligibility/CriterionCard.tsx` 또는 `frontend/src/pages/EligibilityCheckResultPage.tsx`

- [ ] **Step 1: 적합도 결과 렌더 컴포넌트 위치 파악**

Run: `grep -rln 'CriterionItem\|operator.*ANY\|requirement.*displayText' frontend/src --include='*.tsx' | head -10`
Expected: 적합도 결과 렌더 파일 위치 1-2개 도출

- [ ] **Step 2: 백엔드에서 ANY operator 룰의 displayText 처리 확인**

Run: `grep -rln 'RuleOperator\|RequirementFormatter' backend/src/main/java/com/youthfit/eligibility | head -10`
백엔드 `RequirementFormatter.java`가 RuleOperator → 표시 텍스트 변환을 담당. ANY 케이스 추가.

`RequirementFormatter` 파일 열어 다음 메서드의 switch에 ANY 추가 (실제 메서드명/스타일은 파일 따라 조정):

```java
case ANY -> "조건 무관";
```

대응 테스트도 추가/확장:
```java
@Test
void formatRequirement_returns_조건_무관_for_ANY() {
    // ...
}
```

- [ ] **Step 3: 백엔드 빌드/테스트**

Run: `cd backend && ./gradlew test --tests RequirementFormatterTest`
Expected: PASS

- [ ] **Step 4: 프론트 적합도 카드 — `displayText`가 이미 백엔드에서 와서 그대로 렌더되므로 추가 변경 불필요**

확인:
- `requirement.displayText`가 "조건 무관"으로 렌더됨
- `result === 'LIKELY_ELIGIBLE'`인 ANY 룰은 ✅ 아이콘 + 초록 톤 (기존 로직 그대로)

만약 별도 시각 차별화 원하면 카드 컴포넌트에서 `operator === 'ANY'` 분기로 추가 라벨("조건 없음 (통과)") 노출.

`CriterionCard` 또는 동등 컴포넌트에 추가 (시각적 강조):

```tsx
{item.requirement.operator === 'ANY' && (
  <span className="text-xs text-green-700">조건 없음 (통과)</span>
)}
```

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/RequirementFormatter.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/RequirementFormatterTest.java \
        frontend/src/components/eligibility/CriterionCard.tsx
# (실제 변경 파일 경로에 맞춰 조정)
git commit -m "feat: 적합도 결과에서 ANY 룰을 '조건 무관 (통과)' 으로 표시"
```

---

## Phase 10 — 스모크 + 검증

### Task 10.1: 스모크 스크립트 작성

**Files:**
- Create: `/tmp/yc-smoke/run-v2.mjs`

- [ ] **Step 1: 스모크 스크립트 작성**

```js
// PR #82의 /tmp/yc-smoke/run.mjs 확장: 워크플로우 transform 결과에 신규 필드 + rawCodes 검증
import fs from 'node:fs';

const wf = JSON.parse(fs.readFileSync('/Users/taetaetae/IdeaProjects/youthfit/n8n/workflows/youth-center-seoul.json', 'utf8'));
const transformNode = wf.nodes.find(n => n.name === '정책 → IngestPolicyRequest 변환');
const jsCode = transformNode.parameters.jsCode;

const sample = JSON.parse(fs.readFileSync('/tmp/yc-smoke/gangnam25.json', 'utf8'));
const policies = sample.result.youthPolicyList;
console.log(`정책 수: ${policies.length}`);

function runTransform(policy) {
  const $input = { first: () => ({ json: policy }) };
  const wrapper = new Function('$input', `${jsCode}`);
  return wrapper($input);
}

const env = process.env;
const BACKEND_URL = env.BACKEND_URL || 'http://localhost:8080';
const INTERNAL_API_KEY = env.INTERNAL_API_KEY || 'changeme';

const REQUIRED_FIELDS = [
  'screeningMethod', 'submissionDocuments', 'additionalQualification',
  'participationRestriction', 'additionalNotes',
  'businessPeriodStart', 'businessPeriodEnd', 'businessPeriodNote',
  'supportScale', 'firstComeFirstServed', 'applyUrl', 'rawCodes'
];

const REQUIRED_RAW_CODES = [
  'ageMin', 'ageMax', 'ageLimitYn',
  'maritalStatusCd', 'earnConditionCd', 'earnMin', 'earnMax', 'earnEtcCn',
  'employmentKindCd', 'educationCd', 'majorFieldCd', 'specializationCd', 'zipCodes'
];

let transformOk = 0;
let transformFail = 0;
const fails = [];

for (const p of policies) {
  let body;
  try {
    const out = runTransform(p);
    body = out[0].json;
  } catch (e) {
    transformFail++;
    fails.push(`transform 실패 ${p.plcyNo}: ${e.message}`);
    continue;
  }

  // 필드 존재 검증
  const rawData = body.rawData || {};
  const missing = REQUIRED_FIELDS.filter(f => !(f in rawData));
  if (missing.length) {
    transformFail++;
    fails.push(`${p.plcyNo} 누락 rawData 필드: ${missing.join(',')}`);
    continue;
  }
  const rc = rawData.rawCodes;
  if (!rc) {
    transformFail++;
    fails.push(`${p.plcyNo} rawCodes 자체 없음`);
    continue;
  }
  const missingRc = REQUIRED_RAW_CODES.filter(f => !(f in rc));
  if (missingRc.length) {
    transformFail++;
    fails.push(`${p.plcyNo} 누락 rawCodes 필드: ${missingRc.join(',')}`);
    continue;
  }
  transformOk++;
}

console.log(`transform: OK=${transformOk}, FAIL=${transformFail}`);
fails.slice(0, 5).forEach(f => console.log('  ' + f));

if (transformFail > 0) {
  process.exit(1);
}

// 백엔드 POST 단계 (PR #82와 동일)
const ingestResults = { received: 0, skipped: 0, failed: 0, errors: [] };

for (const p of policies) {
  let body;
  try {
    const out = runTransform(p);
    body = out[0].json;
  } catch (e) {
    ingestResults.failed++;
    continue;
  }
  try {
    const res = await fetch(`${BACKEND_URL}/api/internal/ingestion/policies`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Internal-Api-Key': INTERNAL_API_KEY
      },
      body: JSON.stringify(body)
    });
    const text = await res.text();
    if (res.status === 200 || res.status === 202) {
      const parsed = JSON.parse(text);
      const status = parsed.data?.status || parsed.status || 'UNKNOWN';
      if (status === 'SKIPPED_DUPLICATE') ingestResults.skipped++;
      else ingestResults.received++;
      console.log(`[${res.status}] ${p.plcyNm.slice(0, 30)} → ${status}`);
    } else {
      ingestResults.failed++;
      ingestResults.errors.push(`${p.plcyNo}: HTTP ${res.status} ${text.slice(0,150)}`);
    }
  } catch (e) {
    ingestResults.failed++;
    ingestResults.errors.push(`${p.plcyNo}: POST error ${e.message}`);
  }
}

console.log('');
console.log('=== ingest 요약 ===');
console.log(`RECEIVED: ${ingestResults.received}`);
console.log(`SKIPPED_DUPLICATE: ${ingestResults.skipped}`);
console.log(`FAILED: ${ingestResults.failed}`);
ingestResults.errors.slice(0, 5).forEach(e => console.log('  ' + e));
```

- [ ] **Step 2: gangnam25.json 존재 확인**

Run: `ls -la /tmp/yc-smoke/gangnam25.json`
Expected: PR #82 시점 파일 존재. 없으면 다음 명령으로 갱신:

```bash
mkdir -p /tmp/yc-smoke
KEY=$(grep YOUTH_CENTER_API_KEY .env | cut -d= -f2)
curl -s "https://www.youthcenter.go.kr/go/ythip/getPlcy?apiKeyNm=$KEY&rtnType=json&pageNum=1&pageSize=25&zipCd=11680" > /tmp/yc-smoke/gangnam25.json
```

(커밋 없음 — `/tmp` 작업 파일)

---

### Task 10.2: 마이그레이션 적용 + 백엔드 시동 + 스모크 + DB 검증

**Files:** (운영)

- [ ] **Step 1: 백엔드 컨테이너 재빌드 + 재시작**

Run:
```bash
docker compose build backend
docker compose up -d backend
docker compose logs --tail 50 backend
```
Expected: `Started YouthfitApplication`, 에러 없음

- [ ] **Step 2: 백엔드 health 확인**

Run: `curl -s http://localhost:8080/actuator/health || curl -s http://localhost:8080/api/health`
Expected: status UP (혹은 200)

- [ ] **Step 3: 스모크 스크립트 실행**

Run:
```bash
cd /tmp/yc-smoke
INTERNAL_API_KEY=$(grep INTERNAL_API_KEY /Users/taetaetae/IdeaProjects/youthfit/.env | cut -d= -f2) \
node run-v2.mjs
```
Expected:
```
정책 수: 25
transform: OK=25, FAIL=0
[200] ... → RECEIVED  (또는 SKIPPED_DUPLICATE)
=== ingest 요약 ===
RECEIVED: 24~25, SKIPPED_DUPLICATE: 0~1, FAILED: 0
```

- [ ] **Step 4: DB 신규 컬럼 검증**

Run:
```bash
docker exec youthfit-postgres psql -U youthfit -d youthfit -c "
SELECT
  COUNT(*) AS total,
  COUNT(*) FILTER (WHERE additional_qualification IS NOT NULL) AS has_qualification,
  COUNT(*) FILTER (WHERE submission_documents IS NOT NULL) AS has_docs,
  COUNT(*) FILTER (WHERE apply_url IS NOT NULL) AS has_apply_url,
  COUNT(*) FILTER (WHERE first_come_first_served = true) AS first_come
FROM policy
WHERE id IN (SELECT policy_id FROM policy_source WHERE source_type='YOUTH_CENTER');"
```
Expected: total ≥ 25, 다른 카운트는 0보다 큼 (정책마다 NULL 비율 다름)

- [ ] **Step 5: code-v1 룰 검증**

Run:
```bash
docker exec youthfit-postgres psql -U youthfit -d youthfit -c "
SELECT
  COUNT(*) FILTER (WHERE extraction_version='code-v1') AS code_v1_count,
  COUNT(DISTINCT policy_id) FILTER (WHERE extraction_version='code-v1') AS policies_with_code_v1,
  COUNT(*) FILTER (WHERE extraction_version='v1' AND policy_id IN (SELECT policy_id FROM policy_source WHERE source_type='YOUTH_CENTER')) AS leftover_v1_for_youth_center
FROM eligibility_rule;"
```
Expected:
- `code_v1_count` ≈ 25 * 8 = 200 (혹은 dedup으로 약간 적음)
- `policies_with_code_v1` ≈ 25
- `leftover_v1_for_youth_center` = 0 (LLM 룰 모두 deterministic으로 교체됨)

- [ ] **Step 6: 한 정책에 8 카테고리 모두 존재 확인**

Run:
```bash
docker exec youthfit-postgres psql -U youthfit -d youthfit -c "
SELECT field, operator, value, label
FROM eligibility_rule
WHERE extraction_version='code-v1'
  AND policy_id = (SELECT MIN(policy_id) FROM eligibility_rule WHERE extraction_version='code-v1')
ORDER BY field;"
```
Expected: 8 row (age, annualIncome, education, employmentKind, majorField, maritalStatus, region, specializationField)

- [ ] **Step 7: 프론트 정책 상세 페이지 한 건 열기**

브라우저에서 `http://localhost:5173/policies/{youth_center_policy_id}` 열기.
Expected:
- 신규 카드 6-7개 노출 (값 있는 것들)
- "공식 신청 페이지로 이동" CTA 보이고 클릭 시 새 탭 열림
- 사업기간/지원규모 chip 노출 (해당 정책에 값 있을 때)

- [ ] **Step 8: 적합도 결과 페이지 검증**

브라우저에서 같은 정책의 적합도 페이지로 이동.
Expected:
- 8개 카테고리 카드 모두 노출
- "제한없음" 카테고리는 ✅ + "조건 무관 (통과)" 표시
- 사용자 프로필이 일부 필드 비어 있어도 ANY 룰은 ✅ 유지

- [ ] **Step 9: BOKJIRO 정책 회귀 확인**

브라우저에서 BOKJIRO 정책 상세 페이지 열기.
Expected:
- 신규 카드 모두 숨김 (NULL)
- 기존 카드/CTA 동일
- 적합도 페이지에서 LLM 추출 룰만 표시

(커밋 없음 — 검증만)

---

## Self-Review

### Spec coverage

| Spec 섹션 | 대응 task |
|---|---|
| §3 스코프 — 11개 컬럼 추가 | Phase 0, Task 2.1 |
| §3 스코프 — RuleOperator.ANY | Task 1.1 |
| §3 스코프 — IngestPolicyRequest 확장 + RawCodes | Task 5.1, 5.2 |
| §3 스코프 — CodeBasedRuleExtractor + Service | Phase 6 (8개), Task 7.1 |
| §3 스코프 — listener 가드 | Task 7.3 |
| §3 스코프 — 워크플로우 transform | Task 8.1 |
| §3 스코프 — 프론트 카드 + CTA + ANY 표시 | Phase 9 (3 task) |
| §3 스코프 — PolicyResponse 직렬화 | Task 4.1, 4.2 |
| §5 스키마 변경 | Phase 0 |
| §6 워크플로우 transform | Task 8.1 |
| §7 백엔드 DTO/Command/Service | Phase 3, 5 |
| §8 CodeBasedRuleExtractor | Phase 6 |
| §9 프론트 정책 상세 + ANY 표시 | Phase 9 |
| §10 마이그레이션 / 기존 데이터 처리 | Phase 0, Task 10.2 |
| §11 테스트 전략 | 모든 Phase에 TDD 단계 명시 + Phase 10 스모크 |

### Placeholder scan

- "TBD" / "TODO" 없음
- 모든 step에 실제 명령/코드 포함됨
- "Similar to Task N" 같은 표현 없음 (각 카테고리 구현 task에 코드 전체 명시)

### Type 일관성

- `extraction_version` 문자열은 항상 `"code-v1"` (Task 6.1, 7.3)
- `RuleOperator.ANY` enum 값은 모든 사용처에서 일관 (Task 1.1, 6.2~6.9)
- 11개 컬럼 명은 모든 layer에서 동일 (camelCase: Java/TS / snake_case: SQL)
- `firstComeFirstServed` boolean은 nullable Boolean(IngestPolicyRequest) → primitive boolean(RegisterCommand/Policy)으로 unbox: Task 5.3에서 `Boolean.TRUE.equals(...)` 처리 명시

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/DONE_2026-05-09-youth-center-detail-and-deterministic-rules.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
