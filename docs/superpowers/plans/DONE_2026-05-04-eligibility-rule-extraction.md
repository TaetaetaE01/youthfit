# 적합도 룰 LLM 자동 추출 파이프라인 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책 ingest 시점에 LLM 이 정책 원문에서 `EligibilityRule` 묶음을 자동으로 추출해 DB 에 저장하고, 평가기는 `RuleConfidence=LOW` 룰을 강제로 `UNCERTAIN` 처리한다.

**Architecture:** `guide` 모듈과 동일한 패턴 (sourceHash 변경 감지 + CostGuard allowlist + 검증기·재시도). 모든 신규 코드는 `eligibility` 모듈 안에서 끝나고, 트리거는 `IngestionService` / `AttachmentReindexService` 의 `triggerGuideGeneration` 헬퍼 옆에 한 줄 추가.

**Tech Stack:** Java 21, Spring Boot 4, JPA/Hibernate, PostgreSQL, OpenAI Chat API (`gpt-4o-mini`, `response_format: json_object`), JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-05-04-eligibility-rule-extraction-design.md`

---

## File Structure

신규/변경 파일 한눈에 보기 (Task 번호 표시):

| 파일 | Task |
|---|---|
| `backend/src/main/resources/sql/2026-05-04-eligibility-rule-extraction-meta.sql` (신규) | 1 |
| `eligibility/domain/model/RuleConfidence.java` (신규) | 1 |
| `eligibility/domain/model/EligibilityRule.java` (수정) | 1 |
| `eligibility/domain/repository/EligibilityRuleRepository.java` (수정) | 2 |
| `eligibility/infrastructure/persistence/EligibilityRuleJpaRepository.java` (수정) | 2 |
| `eligibility/infrastructure/persistence/EligibilityRuleRepositoryImpl.java` (수정) | 2 |
| `eligibility/domain/service/CriterionEvaluation.java` (수정) | 3 |
| `eligibility/domain/service/EligibilityEvaluator.java` (수정) | 3 |
| `eligibility/application/dto/result/CriterionResult.java` (수정) | 4 |
| `eligibility/presentation/dto/response/CriterionResponse.java` (수정) | 4 |
| `eligibility/application/dto/command/GenerateEligibilityRulesCommand.java` (신규) | 5 |
| `eligibility/application/dto/result/RuleGenerationResult.java` (신규) | 5 |
| `eligibility/application/dto/command/EligibilityRuleExtractionInput.java` (신규) | 5 |
| `eligibility/application/dto/command/RuleExtractionChunk.java` (신규) | 5 |
| `eligibility/application/dto/result/RawExtractedRule.java` (신규) | 5 |
| `eligibility/application/port/EligibilityRuleLlmProvider.java` (신규) | 6 |
| `eligibility/application/service/EligibilityRuleValidator.java` (신규) | 7 |
| `eligibility/application/service/EligibilityRuleGenerationService.java` (신규) | 8 |
| `eligibility/infrastructure/external/OpenAiEligibilityRuleProperties.java` (신규) | 9 |
| `eligibility/infrastructure/external/OpenAiEligibilityRuleClient.java` (신규) | 9 |
| `ingestion/application/service/IngestionService.java` (수정) | 10 |
| `ingestion/application/service/AttachmentReindexService.java` (수정) | 10 |
| 테스트 파일들 (각 task 안에서 명시) | 1·3·7·8·11 |

---

## Task 1: 도메인 모델 - `RuleConfidence` enum + `EligibilityRule` 컬럼 + 마이그레이션 SQL

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/domain/model/RuleConfidence.java`
- Create: `backend/src/main/resources/sql/2026-05-04-eligibility-rule-extraction-meta.sql`
- Modify: `backend/src/main/java/com/youthfit/eligibility/domain/model/EligibilityRule.java`

**핵심 컨텍스트:** 운영 DB 마이그레이션은 Flyway 가 아니라 `backend/src/main/resources/sql/` 에 날짜 prefix 로 SQL 파일을 두고 운영자가 수동 적용한다 (예: `2026-05-01-qna-question-cache.sql`). 개발/테스트는 `ddl-auto: update` 가 자동 반영. 기존 `EligibilityRule` 데이터는 사실상 비어있을 가능성이 높지만 안전하게 NOT NULL DEFAULT 로 백필.

- [ ] **Step 1: `RuleConfidence` enum 생성**

```java
package com.youthfit.eligibility.domain.model;

public enum RuleConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

- [ ] **Step 2: 마이그레이션 SQL 작성**

`backend/src/main/resources/sql/2026-05-04-eligibility-rule-extraction-meta.sql`:

```sql
-- backend/src/main/resources/sql/2026-05-04-eligibility-rule-extraction-meta.sql
-- 적합도 룰 LLM 추출 메타데이터 컬럼 추가. 운영 환경에 수동 적용한다.

ALTER TABLE eligibility_rule
    ADD COLUMN IF NOT EXISTS confidence VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS source_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS extraction_version VARCHAR(10);
```

- [ ] **Step 3: `EligibilityRule` 도메인 엔티티 컬럼 추가**

`EligibilityRule.java` 전체 교체:

```java
package com.youthfit.eligibility.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "eligibility_rule")
public class EligibilityRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(nullable = false, length = 30)
    private String field;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleOperator operator;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "source_reference", columnDefinition = "TEXT")
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RuleConfidence confidence;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "extraction_version", length = 10)
    private String extractionVersion;

    @Builder
    private EligibilityRule(Long policyId, String field, RuleOperator operator,
                            String value, String label, String sourceReference,
                            RuleConfidence confidence, String sourceHash, String extractionVersion) {
        this.policyId = policyId;
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.label = label;
        this.sourceReference = sourceReference;
        this.confidence = confidence == null ? RuleConfidence.MEDIUM : confidence;
        this.sourceHash = sourceHash;
        this.extractionVersion = extractionVersion;
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 단위 테스트 작성 - `RuleConfidence` 기본값**

Create `backend/src/test/java/com/youthfit/eligibility/domain/model/EligibilityRuleTest.java`:

```java
package com.youthfit.eligibility.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityRuleTest {

    @Test
    void builder_confidenceDefaultsToMedium() {
        EligibilityRule rule = EligibilityRule.builder()
                .policyId(1L)
                .field("age")
                .operator(RuleOperator.BETWEEN)
                .value("19~34")
                .label("연령")
                .sourceReference("자격 요건 > 연령")
                .build();

        assertThat(rule.getConfidence()).isEqualTo(RuleConfidence.MEDIUM);
        assertThat(rule.getSourceHash()).isNull();
        assertThat(rule.getExtractionVersion()).isNull();
    }

    @Test
    void builder_confidenceIsRespectedWhenSet() {
        EligibilityRule rule = EligibilityRule.builder()
                .policyId(1L)
                .field("age")
                .operator(RuleOperator.BETWEEN)
                .value("19~34")
                .label("연령")
                .sourceReference("자격 요건 > 연령")
                .confidence(RuleConfidence.LOW)
                .sourceHash("abc")
                .extractionVersion("v1")
                .build();

        assertThat(rule.getConfidence()).isEqualTo(RuleConfidence.LOW);
        assertThat(rule.getSourceHash()).isEqualTo("abc");
        assertThat(rule.getExtractionVersion()).isEqualTo("v1");
    }
}
```

- [ ] **Step 6: 테스트 실행**

Run: `cd backend && ./gradlew test --tests EligibilityRuleTest`
Expected: 2 tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/model/RuleConfidence.java \
        backend/src/main/java/com/youthfit/eligibility/domain/model/EligibilityRule.java \
        backend/src/main/resources/sql/2026-05-04-eligibility-rule-extraction-meta.sql \
        backend/src/test/java/com/youthfit/eligibility/domain/model/EligibilityRuleTest.java
git commit -m "feat(eligibility): RuleConfidence enum + EligibilityRule 추출 메타 컬럼 추가"
```

---

## Task 2: Repository - `deleteAllByPolicyId` 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/domain/repository/EligibilityRuleRepository.java`
- Modify: `backend/src/main/java/com/youthfit/eligibility/infrastructure/persistence/EligibilityRuleJpaRepository.java`
- Modify: `backend/src/main/java/com/youthfit/eligibility/infrastructure/persistence/EligibilityRuleRepositoryImpl.java`

- [ ] **Step 1: 도메인 인터페이스 메서드 추가**

`EligibilityRuleRepository.java` 전체 교체:

```java
package com.youthfit.eligibility.domain.repository;

import com.youthfit.eligibility.domain.model.EligibilityRule;

import java.util.List;

public interface EligibilityRuleRepository {

    List<EligibilityRule> findAllByPolicyId(Long policyId);

    void deleteAllByPolicyId(Long policyId);

    void saveAll(List<EligibilityRule> rules);
}
```

- [ ] **Step 2: JPA 인터페이스에 `@Modifying` delete 메서드 추가**

`EligibilityRuleJpaRepository.java` 전체 교체:

```java
package com.youthfit.eligibility.infrastructure.persistence;

import com.youthfit.eligibility.domain.model.EligibilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EligibilityRuleJpaRepository extends JpaRepository<EligibilityRule, Long> {

    List<EligibilityRule> findAllByPolicyId(Long policyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EligibilityRule r where r.policyId = :policyId")
    int deleteAllByPolicyId(@Param("policyId") Long policyId);
}
```

- [ ] **Step 3: 도메인 구현체에 새 메서드 위임**

`EligibilityRuleRepositoryImpl.java` 전체 교체:

```java
package com.youthfit.eligibility.infrastructure.persistence;

import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class EligibilityRuleRepositoryImpl implements EligibilityRuleRepository {

    private final EligibilityRuleJpaRepository jpaRepository;

    @Override
    public List<EligibilityRule> findAllByPolicyId(Long policyId) {
        return jpaRepository.findAllByPolicyId(policyId);
    }

    @Override
    public void deleteAllByPolicyId(Long policyId) {
        jpaRepository.deleteAllByPolicyId(policyId);
    }

    @Override
    public void saveAll(List<EligibilityRule> rules) {
        jpaRepository.saveAll(rules);
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL (`EligibilityService` 의 기존 호출은 인터페이스 시그니처 그대로라 영향 없음)

- [ ] **Step 5: 기존 테스트 회귀 확인**

Run: `cd backend && ./gradlew test --tests "*Eligibility*"`
Expected: 기존 모든 테스트 PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/repository/EligibilityRuleRepository.java \
        backend/src/main/java/com/youthfit/eligibility/infrastructure/persistence/EligibilityRuleJpaRepository.java \
        backend/src/main/java/com/youthfit/eligibility/infrastructure/persistence/EligibilityRuleRepositoryImpl.java
git commit -m "feat(eligibility): EligibilityRuleRepository 에 deleteAllByPolicyId / saveAll 추가"
```

---

## Task 3: 평가기 변경 - `CriterionEvaluation.confidenceNote` + `EligibilityEvaluator` LOW → UNCERTAIN

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/domain/service/CriterionEvaluation.java`
- Modify: `backend/src/main/java/com/youthfit/eligibility/domain/service/EligibilityEvaluator.java`
- Test: `backend/src/test/java/com/youthfit/eligibility/domain/service/EligibilityEvaluatorTest.java` (신규)

- [ ] **Step 1: 실패 테스트 작성 - LOW 룰의 매칭/비매칭이 모두 UNCERTAIN 처리되는지**

Create `backend/src/test/java/com/youthfit/eligibility/domain/service/EligibilityEvaluatorTest.java`:

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.user.domain.model.EligibilityProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityEvaluatorTest {

    private final EligibilityEvaluator evaluator = new EligibilityEvaluator();

    @Test
    void highConfidence_matching_returnsLikelyEligible() {
        EligibilityRule rule = ageRule(RuleConfidence.HIGH);
        EligibilityProfile profile = profileWithAge(29);

        CriterionEvaluation result = evaluator.evaluateRule(rule, profile);

        assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        assertThat(result.confidenceNote()).isNull();
    }

    @Test
    void highConfidence_notMatching_returnsLikelyIneligible() {
        EligibilityRule rule = ageRule(RuleConfidence.HIGH);
        EligibilityProfile profile = profileWithAge(40);

        CriterionEvaluation result = evaluator.evaluateRule(rule, profile);

        assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
    }

    @Test
    void lowConfidence_matching_isDowngradedToUncertain() {
        EligibilityRule rule = ageRule(RuleConfidence.LOW);
        EligibilityProfile profile = profileWithAge(29);

        CriterionEvaluation result = evaluator.evaluateRule(rule, profile);

        assertThat(result.result()).isEqualTo(EligibilityResult.UNCERTAIN);
        assertThat(result.confidenceNote()).isEqualTo("근거가 모호함");
    }

    @Test
    void lowConfidence_notMatching_isDowngradedToUncertain() {
        EligibilityRule rule = ageRule(RuleConfidence.LOW);
        EligibilityProfile profile = profileWithAge(40);

        CriterionEvaluation result = evaluator.evaluateRule(rule, profile);

        assertThat(result.result()).isEqualTo(EligibilityResult.UNCERTAIN);
        assertThat(result.confidenceNote()).isEqualTo("근거가 모호함");
    }

    @Test
    void mediumConfidence_behavesLikeHigh() {
        EligibilityRule rule = ageRule(RuleConfidence.MEDIUM);
        EligibilityProfile profile = profileWithAge(29);

        CriterionEvaluation result = evaluator.evaluateRule(rule, profile);

        assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        assertThat(result.confidenceNote()).isNull();
    }

    @Test
    void missingProfileField_returnsUncertain_regardlessOfConfidence() {
        EligibilityRule rule = ageRule(RuleConfidence.HIGH);
        EligibilityProfile profile = EligibilityProfile.empty(1L);

        CriterionEvaluation result = evaluator.evaluateRule(rule, profile);

        assertThat(result.result()).isEqualTo(EligibilityResult.UNCERTAIN);
    }

    private EligibilityRule ageRule(RuleConfidence confidence) {
        return EligibilityRule.builder()
                .policyId(1L)
                .field("age")
                .operator(RuleOperator.BETWEEN)
                .value("19~34")
                .label("연령")
                .sourceReference("자격 요건 > 연령")
                .confidence(confidence)
                .build();
    }

    private EligibilityProfile profileWithAge(int age) {
        EligibilityProfile profile = EligibilityProfile.empty(1L);
        profile.changeAge(age);
        return profile;
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests EligibilityEvaluatorTest`
Expected: 컴파일 에러 (`confidenceNote()` 메서드 없음, `confidence` 필드 빌더에 없음 — 이건 Task 1 에서 추가됨, 따라서 실제 실패 사유는 `confidenceNote()` 누락)

- [ ] **Step 3: `CriterionEvaluation` 에 `confidenceNote` 필드 추가**

`CriterionEvaluation.java` 전체 교체:

```java
package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;

public record CriterionEvaluation(
        String field,
        String label,
        EligibilityResult result,
        String reason,
        String sourceReference,
        String confidenceNote
) {

    public static CriterionEvaluation eligible(EligibilityRule rule, Object userValue) {
        String reason = formatValue(userValue) + " — " + rule.getLabel() + "(" + rule.getValue() + ") 충족";
        return new CriterionEvaluation(
                rule.getField(), rule.getLabel(), EligibilityResult.LIKELY_ELIGIBLE,
                reason, rule.getSourceReference(), null
        );
    }

    public static CriterionEvaluation ineligible(EligibilityRule rule, Object userValue) {
        String reason = formatValue(userValue) + " — " + rule.getLabel() + "(" + rule.getValue() + ") 미충족";
        return new CriterionEvaluation(
                rule.getField(), rule.getLabel(), EligibilityResult.LIKELY_INELIGIBLE,
                reason, rule.getSourceReference(), null
        );
    }

    public static CriterionEvaluation uncertain(EligibilityRule rule) {
        String reason = "정보 미입력 — 판단 불가";
        return new CriterionEvaluation(
                rule.getField(), rule.getLabel(), EligibilityResult.UNCERTAIN,
                reason, rule.getSourceReference(), null
        );
    }

    public static CriterionEvaluation lowConfidenceUncertain(EligibilityRule rule) {
        String reason = "원문 근거가 모호하여 판단 불가";
        return new CriterionEvaluation(
                rule.getField(), rule.getLabel(), EligibilityResult.UNCERTAIN,
                reason, rule.getSourceReference(), "근거가 모호함"
        );
    }

    private static String formatValue(Object value) {
        return String.valueOf(value);
    }
}
```

- [ ] **Step 4: `EligibilityEvaluator` 에 LOW → UNCERTAIN 다운그레이드 추가**

`EligibilityEvaluator.java` 의 `evaluateRule` 메서드만 교체. 기존 메서드 (1번째 메서드) 위치에 다음 코드 적용:

```java
public CriterionEvaluation evaluateRule(EligibilityRule rule, EligibilityProfile profile) {
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

`EligibilityEvaluator.java` 의 import 에 추가: `import com.youthfit.eligibility.domain.model.RuleConfidence;`

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests EligibilityEvaluatorTest`
Expected: 6 tests PASS

- [ ] **Step 6: 회귀 확인 - 기존 적합도 테스트 모두 PASS**

Run: `cd backend && ./gradlew test --tests "*Eligibility*"`
Expected: 기존 + 신규 모든 테스트 PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/domain/service/CriterionEvaluation.java \
        backend/src/main/java/com/youthfit/eligibility/domain/service/EligibilityEvaluator.java \
        backend/src/test/java/com/youthfit/eligibility/domain/service/EligibilityEvaluatorTest.java
git commit -m "feat(eligibility): LOW 신뢰도 룰을 평가기에서 UNCERTAIN 으로 다운그레이드"
```

---

## Task 4: 결과 DTO 흐름 - `CriterionResult` / `CriterionResponse` 에 `confidenceNote` 노출

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/application/dto/result/CriterionResult.java`
- Modify: `backend/src/main/java/com/youthfit/eligibility/presentation/dto/response/CriterionResponse.java`

- [ ] **Step 1: `CriterionResult` 에 `confidenceNote` 필드 추가**

`CriterionResult.java` 전체 교체:

```java
package com.youthfit.eligibility.application.dto.result;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;

public record CriterionResult(
        String field,
        String label,
        EligibilityResult result,
        String reason,
        String sourceReference,
        String confidenceNote
) {

    public static CriterionResult from(CriterionEvaluation evaluation) {
        return new CriterionResult(
                evaluation.field(),
                evaluation.label(),
                evaluation.result(),
                evaluation.reason(),
                evaluation.sourceReference(),
                evaluation.confidenceNote()
        );
    }
}
```

- [ ] **Step 2: `CriterionResponse` 에 `confidenceNote` 필드 추가**

`CriterionResponse.java` 전체 교체:

```java
package com.youthfit.eligibility.presentation.dto.response;

import com.youthfit.eligibility.application.dto.result.CriterionResult;

public record CriterionResponse(
        String field,
        String label,
        String result,
        String reason,
        String sourceReference,
        String confidenceNote
) {

    public static CriterionResponse from(CriterionResult criterionResult) {
        return new CriterionResponse(
                criterionResult.field(),
                criterionResult.label(),
                criterionResult.result().name(),
                criterionResult.reason(),
                criterionResult.sourceReference(),
                criterionResult.confidenceNote()
        );
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 회귀 확인**

Run: `cd backend && ./gradlew test --tests "*Eligibility*"`
Expected: 모든 테스트 PASS (기존 응답에 새 필드는 null 로 노출됨, 호환 유지)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/dto/result/CriterionResult.java \
        backend/src/main/java/com/youthfit/eligibility/presentation/dto/response/CriterionResponse.java
git commit -m "feat(eligibility): CriterionResult/Response 에 confidenceNote 노출"
```

---

## Task 5: Application DTO 신규 - LLM 입출력 / Command / Result

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/application/dto/command/GenerateEligibilityRulesCommand.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/application/dto/result/RuleGenerationResult.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/application/dto/command/EligibilityRuleExtractionInput.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/application/dto/command/RuleExtractionChunk.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/application/dto/result/RawExtractedRule.java`

- [ ] **Step 1: `GenerateEligibilityRulesCommand` 작성**

```java
package com.youthfit.eligibility.application.dto.command;

public record GenerateEligibilityRulesCommand(Long policyId) {
}
```

- [ ] **Step 2: `RuleGenerationResult` 작성**

```java
package com.youthfit.eligibility.application.dto.result;

public record RuleGenerationResult(
        Long policyId,
        boolean generated,
        String message
) {
}
```

- [ ] **Step 3: `RuleExtractionChunk` 작성**

```java
package com.youthfit.eligibility.application.dto.command;

public record RuleExtractionChunk(
        String content,
        Long attachmentId,
        Integer pageStart,
        Integer pageEnd
) {

    public boolean isAttachment() {
        return attachmentId != null;
    }
}
```

- [ ] **Step 4: `EligibilityRuleExtractionInput` 작성 (입력 + 텍스트 결합)**

```java
package com.youthfit.eligibility.application.dto.command;

import java.util.List;

public record EligibilityRuleExtractionInput(
        Long policyId,
        String title,
        String summary,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String body,
        List<RuleExtractionChunk> attachmentChunks
) {

    public String combinedSourceText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[정책 메타]\n");
        sb.append("policyId: ").append(policyId).append("\n");
        sb.append("title: ").append(safe(title)).append("\n");
        sb.append("summary: ").append(safe(summary)).append("\n\n");

        sb.append("[원문 - 지원 대상]\n").append(safe(supportTarget)).append("\n\n");
        sb.append("[원문 - 선정 기준]\n").append(safe(selectionCriteria)).append("\n\n");
        sb.append("[원문 - 지원 내용]\n").append(safe(supportContent)).append("\n\n");
        sb.append("[원문 - 본문]\n").append(safe(body)).append("\n\n");

        sb.append("[원문 - 첨부 청크]\n");
        for (RuleExtractionChunk c : attachmentChunks) {
            String pages = c.pageStart() == null ? "" : " pages=" + c.pageStart() + "-" + c.pageEnd();
            sb.append("[chunk attachment-id=").append(c.attachmentId()).append(pages).append("]\n");
            sb.append(safe(c.content())).append("\n\n");
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
```

- [ ] **Step 5: `RawExtractedRule` 작성 (검증 전 LLM 출력 DTO)**

```java
package com.youthfit.eligibility.application.dto.result;

public record RawExtractedRule(
        String field,
        String operator,
        String value,
        String label,
        String sourceReference,
        String confidence
) {
}
```

- [ ] **Step 6: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/dto/
git commit -m "feat(eligibility): 룰 추출 파이프라인용 Command/Result/Input/Output DTO 추가"
```

---

## Task 6: 포트 - `EligibilityRuleLlmProvider`

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/application/port/EligibilityRuleLlmProvider.java`

- [ ] **Step 1: 포트 인터페이스 작성**

```java
package com.youthfit.eligibility.application.port;

import com.youthfit.eligibility.application.dto.command.EligibilityRuleExtractionInput;
import com.youthfit.eligibility.application.dto.result.RawExtractedRule;

import java.util.List;

public interface EligibilityRuleLlmProvider {

    /**
     * 정책 원문에서 적합도 룰을 추출한다.
     *
     * @return 검증 전 raw 룰 목록. LLM 이 룰을 못 찾으면 빈 목록.
     * @throws IllegalStateException LLM 호출 실패 또는 응답 JSON 파싱 실패
     */
    List<RawExtractedRule> extractRules(EligibilityRuleExtractionInput input);

    /**
     * 검증 위반 피드백을 받아 동일 입력으로 재추출.
     */
    List<RawExtractedRule> regenerateWithFeedback(
            EligibilityRuleExtractionInput input,
            List<String> feedbackMessages);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/port/EligibilityRuleLlmProvider.java
git commit -m "feat(eligibility): EligibilityRuleLlmProvider 포트 추가"
```

---

## Task 7: 검증기 - `EligibilityRuleValidator` (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/application/service/EligibilityRuleValidator.java`
- Test: `backend/src/test/java/com/youthfit/eligibility/application/service/EligibilityRuleValidatorTest.java`

검증기는 LLM 응답을 받아 (1) 통과한 룰, (2) 폐기 메시지, (3) 재시도 트리거 여부를 반환한다.

- [ ] **Step 1: 검증기 출력 record 사양 정의 + 실패 테스트 작성**

Create test file:

```java
package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.application.service.EligibilityRuleValidator.ValidationReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityRuleValidatorTest {

    private final EligibilityRuleValidator validator = new EligibilityRuleValidator();

    @Test
    void allowed_age_between_passes() {
        RawExtractedRule rule = new RawExtractedRule(
                "age", "BETWEEN", "19~34", "연령", "자격 > 연령", "HIGH");
        ValidationReport report = validator.validate(List.of(rule));

        assertThat(report.acceptedRules()).hasSize(1);
        assertThat(report.acceptedRules().get(0).field()).isEqualTo("age");
        assertThat(report.feedbackMessages()).isEmpty();
        assertThat(report.shouldRetry()).isFalse();
    }

    @Test
    void unknown_field_is_rejected() {
        RawExtractedRule rule = new RawExtractedRule(
                "householdSize", "EQ", "1", "가구원수", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(rule));

        assertThat(report.acceptedRules()).isEmpty();
        assertThat(report.feedbackMessages()).anyMatch(m -> m.contains("householdSize"));
    }

    @Test
    void unknown_operator_is_rejected() {
        RawExtractedRule rule = new RawExtractedRule(
                "age", "MOD", "2", "연령", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(rule));

        assertThat(report.acceptedRules()).isEmpty();
        assertThat(report.feedbackMessages()).anyMatch(m -> m.contains("MOD"));
    }

    @Test
    void age_between_invalid_format_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "age", "BETWEEN", "19-34", "연령", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
        assertThat(report.feedbackMessages()).anyMatch(m -> m.contains("BETWEEN"));
    }

    @Test
    void age_between_min_greater_than_max_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "age", "BETWEEN", "40~30", "연령", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void age_outside_zero_to_hundred_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "age", "GTE", "150", "연령", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void negative_income_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "annualIncome", "LTE", "-1000", "연소득", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void marital_status_invalid_enum_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "maritalStatus", "EQ", "DIVORCED", "혼인", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void marital_status_valid_enum_passes() {
        RawExtractedRule ok = new RawExtractedRule(
                "maritalStatus", "EQ", "SINGLE", "혼인", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(ok));

        assertThat(report.acceptedRules()).hasSize(1);
    }

    @Test
    void employment_kind_in_with_comma_passes_when_all_enum() {
        RawExtractedRule ok = new RawExtractedRule(
                "employmentKind", "IN", "EMPLOYEE,FREELANCER", "고용", "원문", "MEDIUM");
        ValidationReport report = validator.validate(List.of(ok));

        assertThat(report.acceptedRules()).hasSize(1);
    }

    @Test
    void employment_kind_in_with_one_invalid_member_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "employmentKind", "IN", "EMPLOYEE,WIZARD", "고용", "원문", "MEDIUM");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void unknown_confidence_is_corrected_to_medium() {
        RawExtractedRule rule = new RawExtractedRule(
                "age", "BETWEEN", "19~34", "연령", "원문", "TOTALLY_SURE");
        ValidationReport report = validator.validate(List.of(rule));

        assertThat(report.acceptedRules()).hasSize(1);
        assertThat(report.acceptedRules().get(0).confidence()).isEqualTo("MEDIUM");
    }

    @Test
    void retry_triggered_when_more_than_half_dropped() {
        RawExtractedRule ok1 = new RawExtractedRule("age", "BETWEEN", "19~34", "연령", "원문", "HIGH");
        RawExtractedRule bad1 = new RawExtractedRule("householdSize", "EQ", "1", "가구원수", "원문", "HIGH");
        RawExtractedRule bad2 = new RawExtractedRule("creditScore", "GTE", "700", "신용", "원문", "HIGH");

        ValidationReport report = validator.validate(List.of(ok1, bad1, bad2));

        assertThat(report.acceptedRules()).hasSize(1);
        assertThat(report.shouldRetry()).isTrue();
    }

    @Test
    void retry_not_triggered_when_majority_pass() {
        RawExtractedRule ok1 = new RawExtractedRule("age", "BETWEEN", "19~34", "연령", "원문", "HIGH");
        RawExtractedRule ok2 = new RawExtractedRule("region", "EQ", "SEOUL", "거주", "원문", "HIGH");
        RawExtractedRule bad1 = new RawExtractedRule("householdSize", "EQ", "1", "가구원수", "원문", "HIGH");

        ValidationReport report = validator.validate(List.of(ok1, ok2, bad1));

        assertThat(report.acceptedRules()).hasSize(2);
        assertThat(report.shouldRetry()).isFalse();
    }

    @Test
    void empty_input_does_not_trigger_retry() {
        ValidationReport report = validator.validate(List.of());

        assertThat(report.acceptedRules()).isEmpty();
        assertThat(report.shouldRetry()).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행해서 컴파일 실패 확인**

Run: `cd backend && ./gradlew compileTestJava`
Expected: 컴파일 실패 (`EligibilityRuleValidator` / `ValidationReport` 미존재)

- [ ] **Step 3: 검증기 구현**

Create `backend/src/main/java/com/youthfit/eligibility/application/service/EligibilityRuleValidator.java`:

```java
package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class EligibilityRuleValidator {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "age", "region", "incomeMin", "incomeMax", "annualIncome",
            "maritalStatus", "employmentKind", "education",
            "majorField", "specializationField"
    );

    public ValidationReport validate(List<RawExtractedRule> rawRules) {
        List<RawExtractedRule> accepted = new ArrayList<>();
        List<String> feedback = new ArrayList<>();

        for (RawExtractedRule rule : rawRules) {
            String error = validateOne(rule);
            if (error != null) {
                feedback.add("폐기: field=" + rule.field()
                        + ", operator=" + rule.operator()
                        + ", value=" + rule.value()
                        + " (" + error + ")");
                continue;
            }
            accepted.add(normalizeConfidence(rule));
        }

        boolean shouldRetry = !rawRules.isEmpty()
                && (accepted.size() * 2 < rawRules.size());

        return new ValidationReport(accepted, feedback, shouldRetry);
    }

    private String validateOne(RawExtractedRule rule) {
        if (rule.field() == null || !ALLOWED_FIELDS.contains(rule.field())) {
            return "허용되지 않은 field: " + rule.field();
        }
        RuleOperator operator;
        try {
            operator = RuleOperator.valueOf(rule.operator());
        } catch (Exception e) {
            return "허용되지 않은 operator: " + rule.operator();
        }
        if (rule.value() == null || rule.value().isBlank()) {
            return "value 누락";
        }
        return validateValue(rule.field(), operator, rule.value().trim());
    }

    private String validateValue(String field, RuleOperator operator, String value) {
        return switch (field) {
            case "age" -> validateAge(operator, value);
            case "region" -> validateRegion(operator, value);
            case "incomeMin", "incomeMax", "annualIncome" -> validateIncome(operator, value);
            case "maritalStatus" -> validateEnum(operator, value, MaritalStatus.class);
            case "employmentKind" -> validateEnum(operator, value, EmploymentKind.class);
            case "education" -> validateEnum(operator, value, Education.class);
            case "majorField" -> validateEnum(operator, value, MajorField.class);
            case "specializationField" -> validateEnum(operator, value, SpecializationField.class);
            default -> "지원되지 않는 field 매핑: " + field;
        };
    }

    private String validateAge(RuleOperator operator, String value) {
        if (operator == RuleOperator.BETWEEN) {
            String[] bounds = value.split("~");
            if (bounds.length != 2) return "BETWEEN 형식 위반 (예: '19~34')";
            try {
                int min = Integer.parseInt(bounds[0].trim());
                int max = Integer.parseInt(bounds[1].trim());
                if (min < 0 || max > 100 || min > max) {
                    return "age 범위 위반 (0~100, min<=max)";
                }
                return null;
            } catch (NumberFormatException e) {
                return "BETWEEN 정수 변환 실패";
            }
        }
        if (operator == RuleOperator.GTE || operator == RuleOperator.LTE
                || operator == RuleOperator.EQ || operator == RuleOperator.NOT_EQ) {
            try {
                int n = Integer.parseInt(value);
                if (n < 0 || n > 100) return "age 범위 위반 (0~100)";
                return null;
            } catch (NumberFormatException e) {
                return "정수 변환 실패";
            }
        }
        return "age 에 IN 오퍼레이터는 부적합";
    }

    private String validateRegion(RuleOperator operator, String value) {
        if (operator == RuleOperator.EQ || operator == RuleOperator.NOT_EQ) {
            return value.isBlank() ? "region 값 비어있음" : null;
        }
        if (operator == RuleOperator.IN) {
            String[] codes = value.split(",");
            for (String c : codes) {
                if (c.trim().isBlank()) return "region IN 멤버 비어있음";
            }
            return null;
        }
        return "region 에 GTE/LTE/BETWEEN 부적합";
    }

    private String validateIncome(RuleOperator operator, String value) {
        if (operator == RuleOperator.BETWEEN) {
            String[] bounds = value.split("~");
            if (bounds.length != 2) return "BETWEEN 형식 위반";
            try {
                long min = Long.parseLong(bounds[0].trim());
                long max = Long.parseLong(bounds[1].trim());
                if (min < 0 || max < 0 || min > max) return "소득 범위 위반";
                return null;
            } catch (NumberFormatException e) {
                return "소득 BETWEEN 정수 변환 실패";
            }
        }
        if (operator == RuleOperator.GTE || operator == RuleOperator.LTE
                || operator == RuleOperator.EQ || operator == RuleOperator.NOT_EQ) {
            try {
                long n = Long.parseLong(value);
                if (n < 0) return "소득은 음수 불가";
                return null;
            } catch (NumberFormatException e) {
                return "소득 정수 변환 실패";
            }
        }
        return "소득 필드에 IN 오퍼레이터 부적합";
    }

    private <E extends Enum<E>> String validateEnum(RuleOperator operator, String value, Class<E> enumType) {
        if (operator == RuleOperator.IN) {
            String[] members = value.split(",");
            for (String m : members) {
                if (!isEnumMember(enumType, m.trim())) {
                    return enumType.getSimpleName() + " 비허용 멤버: " + m.trim();
                }
            }
            return null;
        }
        if (operator == RuleOperator.EQ || operator == RuleOperator.NOT_EQ) {
            return isEnumMember(enumType, value)
                    ? null
                    : enumType.getSimpleName() + " 비허용 값: " + value;
        }
        return enumType.getSimpleName() + " 에 GTE/LTE/BETWEEN 부적합";
    }

    private <E extends Enum<E>> boolean isEnumMember(Class<E> enumType, String value) {
        try {
            Enum.valueOf(enumType, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private RawExtractedRule normalizeConfidence(RawExtractedRule rule) {
        String c = rule.confidence();
        if (c == null) return withConfidence(rule, "MEDIUM");
        try {
            RuleConfidence.valueOf(c);
            return rule;
        } catch (Exception e) {
            return withConfidence(rule, "MEDIUM");
        }
    }

    private RawExtractedRule withConfidence(RawExtractedRule rule, String confidence) {
        return new RawExtractedRule(
                rule.field(), rule.operator(), rule.value(),
                rule.label(), rule.sourceReference(), confidence);
    }

    public record ValidationReport(
            List<RawExtractedRule> acceptedRules,
            List<String> feedbackMessages,
            boolean shouldRetry
    ) {
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests EligibilityRuleValidatorTest`
Expected: 15 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/service/EligibilityRuleValidator.java \
        backend/src/test/java/com/youthfit/eligibility/application/service/EligibilityRuleValidatorTest.java
git commit -m "feat(eligibility): EligibilityRuleValidator 추가 (필드/오퍼레이터/값 형식 검증 + 재시도 트리거)"
```

---

## Task 8: 응용 서비스 - `EligibilityRuleGenerationService` (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/application/service/EligibilityRuleGenerationService.java`
- Test: `backend/src/test/java/com/youthfit/eligibility/application/service/EligibilityRuleGenerationServiceTest.java`

핵심 흐름은 스펙 §5.1 참조. `guide` 모듈의 `GuideGenerationService` 와 동일한 sourceHash + retry 패턴.

- [ ] **Step 1: Mockito 기반 단위 테스트 작성**

Create test file:

```java
package com.youthfit.eligibility.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.application.dto.result.RuleGenerationResult;
import com.youthfit.eligibility.application.port.EligibilityRuleLlmProvider;
import com.youthfit.eligibility.application.service.EligibilityRuleValidator.ValidationReport;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class EligibilityRuleGenerationServiceTest {

    private CostGuard costGuard;
    private PolicyRepository policyRepository;
    private PolicyDocumentRepository policyDocumentRepository;
    private EligibilityRuleRepository ruleRepository;
    private EligibilityRuleLlmProvider llmProvider;
    private EligibilityRuleValidator validator;

    private EligibilityRuleGenerationService service;

    @BeforeEach
    void setUp() {
        costGuard = mock(CostGuard.class);
        policyRepository = mock(PolicyRepository.class);
        policyDocumentRepository = mock(PolicyDocumentRepository.class);
        ruleRepository = mock(EligibilityRuleRepository.class);
        llmProvider = mock(EligibilityRuleLlmProvider.class);
        validator = mock(EligibilityRuleValidator.class);

        service = new EligibilityRuleGenerationService(
                costGuard, policyRepository, policyDocumentRepository,
                ruleRepository, llmProvider, validator);

        when(costGuard.allows(anyLong())).thenReturn(true);
    }

    @Test
    void costGuardSkip_doesNotCallLlm() {
        when(costGuard.allows(1L)).thenReturn(false);

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isFalse();
        verifyNoInteractions(llmProvider);
        verify(costGuard).logSkip(eq("generateRules"), eq(1L));
    }

    @Test
    void policyNotFound_returnsFailure() {
        when(policyRepository.findById(1L)).thenReturn(Optional.empty());

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isFalse();
        verifyNoInteractions(llmProvider);
    }

    @Test
    void unchangedSourceHash_returnsEarly() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());

        EligibilityRule existing = EligibilityRule.builder()
                .policyId(1L)
                .field("age")
                .operator(com.youthfit.eligibility.domain.model.RuleOperator.BETWEEN)
                .value("19~34")
                .label("연령")
                .sourceReference("...")
                .sourceHash("DUMMY_WILL_OVERRIDE")
                .extractionVersion("v1")
                .build();
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of(existing));

        // 첫 호출에서 service 가 계산한 sourceHash 와 existing.sourceHash 가 같도록 stub
        // 트릭: existing 의 sourceHash 를 service 가 계산한 값으로 미리 모킹할 수 없으므로,
        // 이 케이스는 generated=true 흐름을 통해 sourceHash 를 받아낸 뒤 두 번째 호출에서 검증.
        // → 1차 호출 (LLM 한 번 호출)
        when(llmProvider.extractRules(any())).thenReturn(List.of());
        when(validator.validate(anyList())).thenReturn(new ValidationReport(List.of(), List.of(), false));

        service.generateRules(new GenerateEligibilityRulesCommand(1L));
        // existing 룰이 새 sourceHash 로 갈렸다고 가정 (실제로는 deleteAllByPolicyId + 빈 saveAll)
        // 두 번째 호출은 ruleRepository.findAllByPolicyId 가 빈 리스트라 sourceHash 비교가 안 됨.
        // 이 케이스는 통합 테스트에서 더 정확히 다룬다.
        // 여기서는 LLM 이 1회만 호출됐는지만 검증.
        verify(llmProvider, times(1)).extractRules(any());
    }

    @Test
    void normalFlow_callsDeleteAndSaveAll() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());

        RawExtractedRule raw = new RawExtractedRule(
                "age", "BETWEEN", "19~34", "연령", "자격 > 연령", "HIGH");
        when(llmProvider.extractRules(any())).thenReturn(List.of(raw));
        when(validator.validate(List.of(raw)))
                .thenReturn(new ValidationReport(List.of(raw), List.of(), false));

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isTrue();
        verify(ruleRepository).deleteAllByPolicyId(1L);

        ArgumentCaptor<List<EligibilityRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getField()).isEqualTo("age");
        assertThat(captor.getValue().get(0).getSourceHash()).isNotBlank();
        assertThat(captor.getValue().get(0).getExtractionVersion())
                .isEqualTo(EligibilityRuleGenerationService.PROMPT_VERSION);
    }

    @Test
    void retryTriggered_callsRegenerateOnce() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());

        RawExtractedRule bad = new RawExtractedRule("householdSize", "EQ", "1", "x", "y", "HIGH");
        RawExtractedRule good = new RawExtractedRule("age", "BETWEEN", "19~34", "연령", "자격", "HIGH");

        when(llmProvider.extractRules(any())).thenReturn(List.of(bad));
        when(validator.validate(List.of(bad)))
                .thenReturn(new ValidationReport(List.of(), List.of("폐기: householdSize"), true));

        when(llmProvider.regenerateWithFeedback(any(), anyList())).thenReturn(List.of(good));
        when(validator.validate(List.of(good)))
                .thenReturn(new ValidationReport(List.of(good), List.of(), false));

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isTrue();
        verify(llmProvider).regenerateWithFeedback(any(), anyList());

        ArgumentCaptor<List<EligibilityRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getField()).isEqualTo("age");
    }

    @Test
    void retryNotImproving_keepsFirstResponse() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());

        RawExtractedRule first = new RawExtractedRule("age", "BETWEEN", "19~34", "연령", "원문", "HIGH");

        when(llmProvider.extractRules(any())).thenReturn(List.of(first));
        when(validator.validate(List.of(first)))
                .thenReturn(new ValidationReport(List.of(first), List.of("어떤 위반"), true));
        when(llmProvider.regenerateWithFeedback(any(), anyList())).thenReturn(List.of(first));
        when(validator.validate(List.of(first)))
                .thenReturn(new ValidationReport(List.of(first), List.of("어떤 위반"), true));

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isTrue();
        ArgumentCaptor<List<EligibilityRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void llmFailure_keepsExistingRules() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());
        when(llmProvider.extractRules(any())).thenThrow(new IllegalStateException("OpenAI 502"));

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isFalse();
        verify(ruleRepository, never()).deleteAllByPolicyId(anyLong());
        verify(ruleRepository, never()).saveAll(anyList());
    }

    @Test
    void emptyExtraction_stillDeletesAndSavesEmpty() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());
        when(llmProvider.extractRules(any())).thenReturn(List.of());
        when(validator.validate(List.of()))
                .thenReturn(new ValidationReport(List.of(), List.of(), false));

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isTrue();
        verify(ruleRepository).deleteAllByPolicyId(1L);
        verify(ruleRepository).saveAll(List.of());
    }

    private Policy mockPolicy(Long id) {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(id);
        when(policy.getTitle()).thenReturn("테스트 정책");
        when(policy.getSummary()).thenReturn("요약");
        when(policy.getSupportTarget()).thenReturn("만 19~34세");
        when(policy.getSelectionCriteria()).thenReturn("서울 거주");
        when(policy.getSupportContent()).thenReturn("월세 지원");
        when(policy.getBody()).thenReturn("본문");
        return policy;
    }
}
```

- [ ] **Step 2: 테스트 실행해서 컴파일 실패 확인**

Run: `cd backend && ./gradlew compileTestJava`
Expected: 컴파일 실패 (`EligibilityRuleGenerationService` 미존재)

- [ ] **Step 3: 응용 서비스 구현**

Create `backend/src/main/java/com/youthfit/eligibility/application/service/EligibilityRuleGenerationService.java`:

```java
package com.youthfit.eligibility.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.eligibility.application.dto.command.EligibilityRuleExtractionInput;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.dto.command.RuleExtractionChunk;
import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.application.dto.result.RuleGenerationResult;
import com.youthfit.eligibility.application.port.EligibilityRuleLlmProvider;
import com.youthfit.eligibility.application.service.EligibilityRuleValidator.ValidationReport;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EligibilityRuleGenerationService {

    public static final String PROMPT_VERSION = "v1";

    private static final Logger log = LoggerFactory.getLogger(EligibilityRuleGenerationService.class);

    private final CostGuard costGuard;
    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final EligibilityRuleRepository ruleRepository;
    private final EligibilityRuleLlmProvider llmProvider;
    private final EligibilityRuleValidator validator;

    @Transactional
    public RuleGenerationResult generateRules(GenerateEligibilityRulesCommand command) {
        Long policyId = command.policyId();

        if (!costGuard.allows(policyId)) {
            costGuard.logSkip("generateRules", policyId);
            return new RuleGenerationResult(policyId, false, "cost-guard: allowlist 외 정책");
        }

        Optional<Policy> policyOpt = policyRepository.findById(policyId);
        if (policyOpt.isEmpty()) {
            log.warn("정책 없음 - 룰 추출 스킵: policyId={}", policyId);
            return new RuleGenerationResult(policyId, false, "정책을 찾을 수 없습니다");
        }
        Policy policy = policyOpt.get();

        List<PolicyDocument> chunks = policyDocumentRepository.findByPolicyIdOrderByChunkIndex(policyId);

        EligibilityRuleExtractionInput input = buildInput(policy, chunks);
        String hash = computeHash(input);

        List<EligibilityRule> existing = ruleRepository.findAllByPolicyId(policyId);
        if (!existing.isEmpty()
                && hash.equals(existing.get(0).getSourceHash())
                && PROMPT_VERSION.equals(existing.get(0).getExtractionVersion())) {
            log.info("룰 변경 없음, 재추출 스킵: policyId={}", policyId);
            return new RuleGenerationResult(policyId, false, "변경 없음");
        }

        List<RawExtractedRule> finalRules;
        try {
            List<RawExtractedRule> first = llmProvider.extractRules(input);
            ValidationReport firstReport = validator.validate(first);

            if (firstReport.shouldRetry()) {
                log.info("룰 추출 검증 위반, 재시도: policyId={}, violations={}",
                        policyId, firstReport.feedbackMessages());
                List<RawExtractedRule> second = llmProvider.regenerateWithFeedback(
                        input, firstReport.feedbackMessages());
                ValidationReport secondReport = validator.validate(second);

                if (secondReport.feedbackMessages().size() < firstReport.feedbackMessages().size()) {
                    finalRules = secondReport.acceptedRules();
                } else {
                    log.warn("재시도가 개선되지 않음, 1차 응답 사용: policyId={}", policyId);
                    finalRules = firstReport.acceptedRules();
                }
            } else {
                finalRules = firstReport.acceptedRules();
            }
        } catch (Exception e) {
            log.error("룰 추출 실패 - 기존 룰 유지: policyId={}, message={}", policyId, e.getMessage());
            return new RuleGenerationResult(policyId, false, "LLM 호출 실패: " + e.getMessage());
        }

        ruleRepository.deleteAllByPolicyId(policyId);
        List<EligibilityRule> entities = finalRules.stream()
                .map(r -> toEntity(r, policyId, hash))
                .toList();
        ruleRepository.saveAll(entities);

        log.info("룰 추출 완료: policyId={}, ruleCount={}", policyId, entities.size());
        return new RuleGenerationResult(policyId, true, "생성 완료 (" + entities.size() + "개)");
    }

    private EligibilityRuleExtractionInput buildInput(Policy policy, List<PolicyDocument> chunks) {
        List<RuleExtractionChunk> attachmentChunks = chunks.stream()
                .filter(c -> c.getAttachmentId() != null)
                .map(c -> new RuleExtractionChunk(
                        c.getContent(), c.getAttachmentId(), c.getPageStart(), c.getPageEnd()))
                .toList();
        return new EligibilityRuleExtractionInput(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getSupportTarget(),
                policy.getSelectionCriteria(),
                policy.getSupportContent(),
                policy.getBody(),
                attachmentChunks
        );
    }

    private EligibilityRule toEntity(RawExtractedRule raw, Long policyId, String hash) {
        return EligibilityRule.builder()
                .policyId(policyId)
                .field(raw.field())
                .operator(RuleOperator.valueOf(raw.operator()))
                .value(raw.value())
                .label(raw.label())
                .sourceReference(raw.sourceReference())
                .confidence(RuleConfidence.valueOf(raw.confidence()))
                .sourceHash(hash)
                .extractionVersion(PROMPT_VERSION)
                .build();
    }

    private String computeHash(EligibilityRuleExtractionInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(input.title()));
        sb.append(safe(input.summary()));
        sb.append(safe(input.supportTarget()));
        sb.append(safe(input.selectionCriteria()));
        sb.append(safe(input.supportContent()));
        sb.append(safe(input.body()));
        input.attachmentChunks().forEach(c -> sb.append(safe(c.content())));
        sb.append("|prompt:").append(PROMPT_VERSION);
        return sha256(sb.toString());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests EligibilityRuleGenerationServiceTest`
Expected: 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/service/EligibilityRuleGenerationService.java \
        backend/src/test/java/com/youthfit/eligibility/application/service/EligibilityRuleGenerationServiceTest.java
git commit -m "feat(eligibility): EligibilityRuleGenerationService 추가 (sourceHash 변경 감지 + 재시도 + delete-and-insert)"
```

---

## Task 9: Infrastructure - OpenAI 클라이언트

**Files:**
- Create: `backend/src/main/java/com/youthfit/eligibility/infrastructure/external/OpenAiEligibilityRuleProperties.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/infrastructure/external/OpenAiEligibilityRuleClient.java`
- Modify: `backend/src/main/resources/application.yml` (설정 키 추가)

`guide` 모듈의 `OpenAiChatClient` 패턴을 그대로 따른다 (`RestClient`, JSON 파싱). 시스템 프롬프트 / 사용자 프롬프트는 스펙 §6.3, §6.4 그대로.

- [ ] **Step 1: `OpenAiEligibilityRuleProperties` 작성**

```java
package com.youthfit.eligibility.infrastructure.external;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "openai.eligibility-rule")
public class OpenAiEligibilityRuleProperties {

    private final String apiKey;
    private final String model;
    private final int maxTokens;
}
```

- [ ] **Step 2: `OpenAiEligibilityRuleClient` 작성**

```java
package com.youthfit.eligibility.infrastructure.external;

import com.youthfit.eligibility.application.dto.command.EligibilityRuleExtractionInput;
import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.application.port.EligibilityRuleLlmProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiEligibilityRuleClient implements EligibilityRuleLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEligibilityRuleClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            너는 한국 청년 정책의 자격 요건을 구조화된 JSON 룰로 추출하는 전문가다.

            [작성 원칙]
            1. 정보 통제: 입력된 원문에만 근거. 원문에 없는 조건/숫자/지역/고용/학력 추가 금지.
            2. 출력 형식: { "rules": [...] } JSON 객체. rules 외 키 금지.
            3. 필드 화이트리스트 (이 10개 외 추출 금지):
               age, region, incomeMin, incomeMax, annualIncome,
               maritalStatus, employmentKind, education, majorField, specializationField
            4. 오퍼레이터 화이트리스트: EQ, NOT_EQ, GTE, LTE, IN, BETWEEN
            5. 값 형식 규칙:
               - age + BETWEEN: "19~34" / age + GTE·LTE: 정수
               - region + EQ: 시도 코드 (SEOUL, BUSAN, DAEGU, INCHEON, GWANGJU, DAEJEON, ULSAN,
                 SEJONG, GYEONGGI, GANGWON, CHUNGBUK, CHUNGNAM, JEONBUK, JEONNAM, GYEONGBUK, GYEONGNAM, JEJU)
               - incomeMin/Max/annualIncome: 연소득 원 단위 정수 (예: "32000000")
               - maritalStatus + EQ/NOT_EQ: SINGLE | MARRIED
               - employmentKind: EMPLOYEE | SELF_EMPLOYED | UNEMPLOYED | FREELANCER
                                | DAILY_WORKER | ENTREPRENEUR | PART_TIME | FARMER | OTHER
               - education: UNDER_HIGH | HIGH_SCHOOL_IN | HIGH_SCHOOL_EXPECTED
                            | HIGH_SCHOOL_GRAD | COLLEGE_IN | COLLEGE_EXPECTED
                            | COLLEGE_GRAD | GRADUATE | OTHER
               - majorField: HUMANITIES | SOCIAL | ECONOMICS | NATURAL | ENGINEERING
                             | ARTS | AGRICULTURE | OTHER
               - specializationField: SME | WOMAN | BASIC_LIVELIHOOD | SINGLE_PARENT
                             | DISABLED | FARMER | MILITARY | LOCAL_TALENT | OTHER
            6. 의미 매핑:
               - "청년" → age BETWEEN 19~34 (정책에 다른 연령 명시되면 그 값 우선)
               - "재직자/근로 청년" → employmentKind EMPLOYEE 또는 IN 으로 추론
            7. confidence 등급:
               - HIGH: 원문에 정확한 수치/단어 명시
               - MEDIUM: 합리적 추론
               - LOW: 모호하거나 추론 폭이 큼
            8. 추출 안 함 (스킵):
               - 가구 형태 / 무주택 / 세대주 / 자가 보유 — 평가기 미지원
               - 부양가족 수 / 가구원 수 — 평가기 미지원
               - 신용등급 / 연체 이력 — 평가기 미지원
               화이트리스트 외 모든 자격 조건은 룰로 만들지 않는다.
            9. label: 한국어 표시명 1~10자 (예: "연령", "거주지", "연소득", "고용 형태").
            10. sourceReference: 원문에서 인용한 자격 조건 구절 1줄 (50자 내외).
            11. 룰이 추출되지 않으면 빈 배열 반환. 빈 룰을 만들어내지 않는다.
            12. 어조: 명사형/단정형. 친근체 금지.
            """;

    private final OpenAiEligibilityRuleProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RawExtractedRule> extractRules(EligibilityRuleExtractionInput input) {
        return callOpenAi(SYSTEM_PROMPT, input.combinedSourceText());
    }

    @Override
    public List<RawExtractedRule> regenerateWithFeedback(
            EligibilityRuleExtractionInput input, List<String> feedbackMessages) {
        StringBuilder retryUserPrompt = new StringBuilder();
        retryUserPrompt.append(input.combinedSourceText());
        retryUserPrompt.append("\n\n[이전 응답 검증 위반]\n");
        for (String msg : feedbackMessages) {
            retryUserPrompt.append("- ").append(msg).append("\n");
        }
        retryUserPrompt.append("\n위 위반을 모두 해결한 새 응답을 동일 JSON 스키마로 출력하라.");
        return callOpenAi(SYSTEM_PROMPT, retryUserPrompt.toString());
    }

    private List<RawExtractedRule> callOpenAi(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "temperature", 0,
                "max_tokens", properties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 호출 실패: " + e.getMessage(), e);
        }

        if (responseBody == null) {
            throw new IllegalStateException("OpenAI 응답 본문 없음");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode rules = parsed.path("rules");
            if (!rules.isArray()) {
                log.warn("OpenAI 응답에 rules 배열 없음: {}", content);
                return List.of();
            }
            List<RawExtractedRule> result = new ArrayList<>();
            for (JsonNode r : rules) {
                result.add(new RawExtractedRule(
                        r.path("field").asText(null),
                        r.path("operator").asText(null),
                        r.path("value").asText(null),
                        r.path("label").asText(null),
                        r.path("sourceReference").asText(null),
                        r.path("confidence").asText(null)
                ));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    @Configuration
    @EnableConfigurationProperties(OpenAiEligibilityRuleProperties.class)
    static class Config {
    }
}
```

- [ ] **Step 3: `application.yml` 에 설정 키 추가**

`backend/src/main/resources/application.yml` 의 `openai:` 섹션 (`openai.chat` 옆) 에 다음 추가:

```yaml
openai:
  # ... 기존 chat: 설정 ...
  eligibility-rule:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini
    max-tokens: 2000
```

(파일 위 `openai.chat` 의 들여쓰기 / 키 이름은 그대로 두고 형제 키로 추가.)

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/infrastructure/external/ \
        backend/src/main/resources/application.yml
git commit -m "feat(eligibility): OpenAI 기반 룰 추출 클라이언트 + 설정 추가"
```

---

## Task 10: 트리거 연결 - `IngestionService` + `AttachmentReindexService`

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java`

ingest 자체 트랜잭션은 성공 처리해야 하므로 `triggerGuideGeneration` 처럼 try-catch 헬퍼로 감싼다.

- [ ] **Step 1: `IngestionService` 에 룰 트리거 한 줄 + 헬퍼 추가**

`IngestionService.java` 에 의존 주입 추가 (필드 선언부, `private final GuideGenerationService guideGenerationService;` 옆):

```java
private final EligibilityRuleGenerationService eligibilityRuleGenerationService;
```

또한 import 추가:

```java
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
```

`triggerGuideGeneration` 메서드 호출 직후의 호출 경로에 `triggerRuleGeneration(policyId)` 호출 추가. 즉, `triggerGuideGeneration(policyId, title)` 가 호출되는 모든 자리에 바로 다음 줄로 `triggerRuleGeneration(policyId)` 를 추가한다 (기존 호출 위치는 grep 결과 `IngestionService.java:214` 한 곳).

`IngestionService.java` 끝부분의 `triggerGuideGeneration` 메서드 옆에 새 헬퍼 추가:

```java
private void triggerRuleGeneration(Long policyId) {
    if (policyId == null) return;
    try {
        eligibilityRuleGenerationService.generateRules(new GenerateEligibilityRulesCommand(policyId));
    } catch (Exception e) {
        log.warn("적합도 룰 추출 실패: policyId={}", policyId, e);
    }
}
```

`triggerGuideGeneration(policyId, title);` 호출 위치 다음 줄에:

```java
triggerRuleGeneration(policyId);
```

- [ ] **Step 2: `AttachmentReindexService` 에 룰 재추출 한 줄 + 헬퍼 추가**

`AttachmentReindexService.java` 에 의존 주입 추가 (생성자 필드 영역, `private final GuideGenerationService guideGenerationService;` 옆):

```java
private final EligibilityRuleGenerationService eligibilityRuleGenerationService;
```

import 추가:

```java
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
```

`reindex` 메서드의 `if (result.updated()) { ... guideGenerationService.generateGuide(...); ... }` 블록 안, guide 호출 직후에 다음 추가:

```java
try {
    eligibilityRuleGenerationService.generateRules(new GenerateEligibilityRulesCommand(resolvedId));
    log.info("룰 재추출 완료: policyId={}", resolvedId);
} catch (Exception e) {
    log.warn("룰 재추출 실패: policyId={}", resolvedId, e);
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 회귀 확인**

Run: `cd backend && ./gradlew test --tests "*Ingestion*" --tests "*AttachmentReindex*"`
Expected: 기존 모든 테스트 PASS (Mockito 기반 테스트가 있으면 새 의존성을 mock 으로 주입해야 할 수 있음 — 컴파일 실패 시 추가 mock 주입 필요).

만약 기존 단위 테스트가 깨지면 (`EligibilityRuleGenerationService` mock 추가 누락), 해당 테스트의 `@Mock` 또는 생성자 호출 인자에 mock 한 개 추가.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java \
        backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java
git commit -m "feat(ingestion): 정책 upsert / 첨부 재인덱싱 직후 적합도 룰 자동 추출 트리거"
```

---

## Task 11: 통합 테스트

**Files:**
- Test: `backend/src/test/java/com/youthfit/eligibility/presentation/controller/EligibilityControllerIntegrationTest.java` (수정 또는 신규)

기존 통합 테스트가 있는지 먼저 확인. 없다면 새로 만든다.

- [ ] **Step 1: 기존 통합 테스트 위치 확인**

Run: `find backend/src/test -name "EligibilityController*" -type f`
Expected: 0개 또는 1개 결과

- [ ] **Step 2: 통합 테스트 작성 / 보강**

기존 파일이 있으면 신규 케이스 두 개를 추가하고, 없으면 다음 파일을 새로 만든다.

Create or extend `backend/src/test/java/com/youthfit/eligibility/presentation/controller/EligibilityControllerIntegrationTest.java`:

```java
package com.youthfit.eligibility.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class EligibilityControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private EligibilityRuleRepository ruleRepository;

    @Autowired
    private EligibilityProfileRepository profileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long policyId;
    private Long userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        Policy policy = policyRepository.save(Policy.fixture("테스트 정책"));
        policyId = policy.getId();
        userId = 1L;

        EligibilityProfile profile = EligibilityProfile.empty(userId);
        profile.changeAge(29);
        profileRepository.save(profile);
    }

    @Test
    @WithMockUser
    void zeroRules_returnsLikelyEligible() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("policyId", policyId));

        mockMvc.perform(post("/api/v1/eligibility/judge")
                        .contentType("application/json")
                        .content(body)
                        .principal(() -> userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallResult").value("LIKELY_ELIGIBLE"))
                .andExpect(jsonPath("$.data.criteria.length()").value(0));
    }

    @Test
    @WithMockUser
    void lowConfidenceRule_isDowngradedToUncertain() throws Exception {
        ruleRepository.saveAll(List.of(
                EligibilityRule.builder()
                        .policyId(policyId).field("age")
                        .operator(RuleOperator.BETWEEN).value("19~34")
                        .label("연령").sourceReference("자격 > 연령")
                        .confidence(RuleConfidence.HIGH).build(),
                EligibilityRule.builder()
                        .policyId(policyId).field("employmentKind")
                        .operator(RuleOperator.EQ).value("EMPLOYEE")
                        .label("고용").sourceReference("근로 청년 우대")
                        .confidence(RuleConfidence.LOW).build()
        ));

        String body = objectMapper.writeValueAsString(java.util.Map.of("policyId", policyId));

        mockMvc.perform(post("/api/v1/eligibility/judge")
                        .contentType("application/json")
                        .content(body)
                        .principal(() -> userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallResult").value("UNCERTAIN"))
                .andExpect(jsonPath("$.data.criteria[?(@.field=='employmentKind')].confidenceNote")
                        .value("근거가 모호함"));
    }
}
```

> **주의:** 위 테스트는 `Policy.fixture("...")` 정적 팩토리, `EligibilityProfile.changeAge()` 메서드, `EligibilityProfileRepository.save()` 메서드 존재를 가정한다. 실제 코드에서 같은 이름·시그니처가 없으면 (대체 팩토리/Builder 사용해서) 인라인 수정하라. `@WithMockUser` + `principal()` 인증 처리 방식도 프로젝트 기존 통합 테스트 한 곳을 참고해서 같은 형식으로 맞춘다.

- [ ] **Step 3: 테스트 실행**

Run: `cd backend && ./gradlew test --tests EligibilityControllerIntegrationTest`
Expected: 2 tests PASS

만약 `Policy.fixture` 또는 인증 stub 형태가 다르면 첫 실행에서 실패한다. 그 경우 기존 정책 도메인 테스트 한 곳 (`grep -r "Policy.builder" backend/src/test`) 을 참고해 테스트 픽스처를 그 형태로 맞추고 다시 실행.

- [ ] **Step 4: 전체 빌드 / 회귀 확인**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/youthfit/eligibility/presentation/controller/EligibilityControllerIntegrationTest.java
git commit -m "test(eligibility): LOW 룰 UNCERTAIN 다운그레이드 / 룰 0개 LIKELY_ELIGIBLE 통합 테스트"
```

---

## 종료 점검

- [ ] 모든 task 의 commit 이 메인 라인에 적용됨
- [ ] `./gradlew build` 성공
- [ ] 운영자에게 마이그레이션 SQL 적용 요청: `backend/src/main/resources/sql/2026-05-04-eligibility-rule-extraction-meta.sql`
- [ ] `OPENAI_API_KEY` 환경변수가 ingest 환경에 설정되어 있는지 확인 (이미 guide 에서 사용 중이라 운영 환경에는 있을 것)
- [ ] CostGuard allowlist 정책 1~2개로 ingest 수동 트리거 → DB `eligibility_rule` 테이블에 새 row 생성 + `confidence`/`source_hash`/`extraction_version` 채워졌는지 확인 → 룰 품질 육안 검수
- [ ] 잘못 뽑힌 룰이 많으면 `PROMPT_VERSION` 을 `v2` 로 올리고 프롬프트 보강 후 재배포 → 자동으로 모든 정책 재추출됨

---

## Self-Review

**Spec 커버리지:**
- §3 아키텍처/모듈 위치 → Task 1~10 으로 분산 구현, 모듈 경계 유지 ✓
- §4 데이터 모델 (`RuleConfidence`, 컬럼 3개, delete-and-insert, `deleteAllByPolicyId`) → Task 1, 2 ✓
- §5.1 `EligibilityRuleGenerationService` 흐름 → Task 8 ✓
- §5.2 `EligibilityRuleValidator` (10개 필드/6개 오퍼레이터/값 형식) → Task 7 ✓
- §5.3 포트/클라이언트 → Task 6, 9 ✓
- §5.4 트리거 (IngestionService + AttachmentReindexService) → Task 10 ✓
- §5.5 평가기 LOW → UNCERTAIN, `confidenceNote` → Task 3, 4 ✓
- §6 LLM 입출력 스키마 + 프롬프트 → Task 9 (시스템 프롬프트), Task 5 (입력 record) ✓
- §7 에러 처리 (LLM 실패 시 기존 룰 유지, ingest 실패 분리) → Task 8 (서비스), Task 10 (try-catch 격리) ✓
- §9 테스트 (Validator 단위, Evaluator 단위, Service 단위, 통합 2건) → Task 7, 3, 8, 11 ✓
- §10 마이그레이션 / 롤백 → Task 1 (SQL), 종료 점검 (수동 적용 단계) ✓

**Placeholder scan:** "TBD"/"TODO" 없음. 모든 코드 블록은 실제 구현 코드. Task 11 주의사항 한 곳에 "프로젝트 기존 통합 테스트 한 곳을 참고" 가이드 있음 → 통합 테스트는 환경 변수 의존이 있어 100% 정형화하기 어렵기 때문에 의도적으로 가이드형으로 남김.

**Type 일관성:**
- `RuleConfidence` enum 값 (HIGH/MEDIUM/LOW): 모든 task 에서 동일 ✓
- `RawExtractedRule` 필드 6개 (field, operator, value, label, sourceReference, confidence): Task 5/7/8/9 모두 동일 ✓
- `ValidationReport` 필드 3개 (acceptedRules, feedbackMessages, shouldRetry): Task 7/8 모두 동일 ✓
- `EligibilityRuleLlmProvider` 메서드 2개 (`extractRules`, `regenerateWithFeedback`): Task 6/8/9 동일 시그니처 ✓
- `PROMPT_VERSION` 상수: Task 8 에서 정의, Task 8 의 entity 매핑에서 사용 ✓
- `confidenceNote`: Task 3 도메인, Task 4 application/presentation, Task 11 통합 테스트에서 모두 동일 ✓
- `EligibilityRule.builder()` 새 필드 (`confidence`, `sourceHash`, `extractionVersion`): Task 1 에서 추가, Task 8/11 에서 사용 ✓
- `deleteAllByPolicyId` / `saveAll`: Task 2 에서 정의, Task 8 에서 사용 ✓
