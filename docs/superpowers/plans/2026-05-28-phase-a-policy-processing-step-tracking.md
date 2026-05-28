# Phase A: 백엔드 처리 단계 추적 인프라 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 신규 테이블 `policy_processing_step` + 헬퍼 서비스 + 4개 listener / 1개 ingestion 통합으로 정책의 5단계 (INGESTION / ENRICHMENT / GUIDE / RULE / RAG_INDEXING) 진행 상태를 추적한다. #126 회귀 테스트로 RAG indexing listener 가 실제로 fire 되는지 보장한다.

**Architecture:** JPA Entity 1개 + Repository (port + jpa + impl) + 도메인 서비스 1개 (`PolicyProcessingStepService.markStarted/markFinished`). 5개 진입점 (IngestionService, GuideGenerationEventListener, EligibilityRuleGenerationEventListener, RagIndexingEventListener) 이 동일 헬퍼 호출. policy.processing_status 컬럼은 Phase B 에서 추가하므로 본 Phase 는 step 테이블만 다룬다.

**Tech Stack:** Java 21 + Spring Boot 4 + Hibernate (ddl-auto=update) + PostgreSQL + JUnit 5 + AssertJ + Mockito. 도메인 모듈: `policy` (정책 도메인의 정책 처리 추적이라 자연스러움). 의존 방향: guide / eligibility / rag → policy (기존 PolicyRepository 의존과 동일 패턴).

**선행 조건:**
- `docs/superpowers/specs/2026-05-28-policy-enrichment-tracking-design.md` 의 섹션 4-3, 5 참조.
- 로컬에서 `docker compose up -d` 동작 가능.
- 본 Phase 는 사용자 노출에 영향 없음 (관측만 추가).

---

## Task 1: ProcessingStep + ProcessingStatus enum 정의

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/ProcessingStep.java`
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/ProcessingStatus.java`

본 단계는 type 정의만이라 별도 단위 테스트 없음. 다음 Task 의 엔티티 단위 테스트에서 사용된다.

- [ ] **Step 1: `ProcessingStep.java` 작성**

```java
package com.youthfit.policy.domain.model;

public enum ProcessingStep {
    INGESTION,
    ENRICHMENT,
    GUIDE,
    RULE,
    RAG_INDEXING
}
```

- [ ] **Step 2: `ProcessingStatus.java` 작성**

```java
package com.youthfit.policy.domain.model;

public enum ProcessingStatus {
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    SKIPPED
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/ProcessingStep.java backend/src/main/java/com/youthfit/policy/domain/model/ProcessingStatus.java
git commit -m "feat: ProcessingStep · ProcessingStatus enum 추가"
```

---

## Task 2: `PolicyProcessingStep` 엔티티

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/PolicyProcessingStep.java`
- Test: `backend/src/test/java/com/youthfit/policy/domain/model/PolicyProcessingStepTest.java`

엔티티의 도메인 메서드 `start`, `finish(status, reason, detailJson)` 를 TDD 로 작성.

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyProcessingStepTest {

    @Test
    void start_는_IN_PROGRESS_status_와_started_at_을_세팅한다() {
        PolicyProcessingStep step = PolicyProcessingStep.start(1L, ProcessingStep.INGESTION, 1);

        assertThat(step.getPolicyId()).isEqualTo(1L);
        assertThat(step.getStep()).isEqualTo(ProcessingStep.INGESTION);
        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.IN_PROGRESS);
        assertThat(step.getAttempt()).isEqualTo(1);
        assertThat(step.getStartedAt()).isNotNull();
        assertThat(step.getFinishedAt()).isNull();
        assertThat(step.getDurationMs()).isNull();
    }

    @Test
    void finish_는_상태_종료시각_소요시간을_세팅한다() {
        PolicyProcessingStep step = PolicyProcessingStep.start(1L, ProcessingStep.GUIDE, 1);
        Instant start = step.getStartedAt();

        step.finish(ProcessingStatus.SUCCESS, null, null);

        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(step.getFinishedAt()).isAfterOrEqualTo(start);
        assertThat(step.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(step.getReason()).isNull();
    }

    @Test
    void finish_FAILED_는_reason_을_500자로_truncate_한다() {
        PolicyProcessingStep step = PolicyProcessingStep.start(1L, ProcessingStep.RAG_INDEXING, 1);
        String longReason = "x".repeat(800);

        step.finish(ProcessingStatus.FAILED, longReason, null);

        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(step.getReason()).hasSize(500);
    }

    @Test
    void finish_SKIPPED_는_detail_json_을_보관한다() {
        PolicyProcessingStep step = PolicyProcessingStep.start(1L, ProcessingStep.ENRICHMENT, 1);
        String detail = "{\"skippedUrls\":[{\"url\":\"https://x.com\",\"reason\":\"SPA_DETECTED\"}]}";

        step.finish(ProcessingStatus.SKIPPED, "ALL_URLS_SKIPPED", detail);

        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.SKIPPED);
        assertThat(step.getReason()).isEqualTo("ALL_URLS_SKIPPED");
        assertThat(step.getDetailJson()).isEqualTo(detail);
    }
}
```

- [ ] **Step 2: 테스트 실행해서 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.domain.model.PolicyProcessingStepTest" -q`
Expected: FAIL — `PolicyProcessingStep` 클래스가 존재하지 않음.

- [ ] **Step 3: `PolicyProcessingStep.java` 작성**

```java
package com.youthfit.policy.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

@Entity
@Getter
@Table(
        name = "policy_processing_step",
        indexes = {
                @Index(name = "idx_pps_policy_step", columnList = "policy_id, step"),
                @Index(name = "idx_pps_status_finished", columnList = "status, finished_at DESC"),
                @Index(name = "idx_pps_step_status", columnList = "step, status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_pps_policy_step_attempt",
                        columnNames = {"policy_id", "step", "attempt"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyProcessingStep {

    private static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false, length = 20)
    private ProcessingStep step;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProcessingStatus status;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "reason", length = MAX_REASON_LENGTH)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PolicyProcessingStep start(Long policyId, ProcessingStep step, int attempt) {
        PolicyProcessingStep entity = new PolicyProcessingStep();
        entity.policyId = policyId;
        entity.step = step;
        entity.status = ProcessingStatus.IN_PROGRESS;
        entity.attempt = attempt;
        Instant now = Instant.now();
        entity.startedAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void finish(ProcessingStatus newStatus, String reason, String detailJson) {
        this.status = newStatus;
        this.reason = truncate(reason);
        this.detailJson = detailJson;
        Instant now = Instant.now();
        this.finishedAt = now;
        this.durationMs = (int) Math.max(0, Duration.between(this.startedAt, now).toMillis());
        this.updatedAt = now;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_REASON_LENGTH ? s : s.substring(0, MAX_REASON_LENGTH);
    }
}
```

- [ ] **Step 4: 테스트 다시 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.domain.model.PolicyProcessingStepTest" -q`
Expected: PASS — 4 테스트 모두 성공.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/PolicyProcessingStep.java backend/src/test/java/com/youthfit/policy/domain/model/PolicyProcessingStepTest.java
git commit -m "feat: PolicyProcessingStep 엔티티 + 도메인 메서드 (start, finish)"
```

---

## Task 3: Repository (port + JpaRepository + Impl)

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyProcessingStepRepository.java`
- Create: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepJpaRepository.java`
- Create: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepRepositoryImpl.java`

기존 모듈의 Repository 패턴 (`PolicyDocumentRepository` 등) 과 동일 — port interface + JpaRepository + Impl 클래스.

- [ ] **Step 1: port interface 작성**

```java
package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStep;

import java.util.List;
import java.util.Optional;

public interface PolicyProcessingStepRepository {

    PolicyProcessingStep save(PolicyProcessingStep step);

    Optional<PolicyProcessingStep> findById(Long id);

    List<PolicyProcessingStep> findByPolicyIdOrderByStep(Long policyId);

    /** 동일 (policy_id, step) 의 가장 큰 attempt 행 1건. */
    Optional<PolicyProcessingStep> findLatestByPolicyIdAndStep(Long policyId, ProcessingStep step);

    int countByPolicyIdAndStep(Long policyId, ProcessingStep step);
}
```

- [ ] **Step 2: JpaRepository 작성**

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyProcessingStepJpaRepository extends JpaRepository<PolicyProcessingStep, Long> {

    List<PolicyProcessingStep> findByPolicyIdOrderByStepAscAttemptAsc(Long policyId);

    Optional<PolicyProcessingStep> findTopByPolicyIdAndStepOrderByAttemptDesc(Long policyId, ProcessingStep step);

    int countByPolicyIdAndStep(Long policyId, ProcessingStep step);
}
```

- [ ] **Step 3: Impl 작성**

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PolicyProcessingStepRepositoryImpl implements PolicyProcessingStepRepository {

    private final PolicyProcessingStepJpaRepository jpaRepository;

    @Override
    public PolicyProcessingStep save(PolicyProcessingStep step) {
        return jpaRepository.save(step);
    }

    @Override
    public Optional<PolicyProcessingStep> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<PolicyProcessingStep> findByPolicyIdOrderByStep(Long policyId) {
        return jpaRepository.findByPolicyIdOrderByStepAscAttemptAsc(policyId);
    }

    @Override
    public Optional<PolicyProcessingStep> findLatestByPolicyIdAndStep(Long policyId, ProcessingStep step) {
        return jpaRepository.findTopByPolicyIdAndStepOrderByAttemptDesc(policyId, step);
    }

    @Override
    public int countByPolicyIdAndStep(Long policyId, ProcessingStep step) {
        return jpaRepository.countByPolicyIdAndStep(policyId, step);
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicyProcessingStepRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepJpaRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepRepositoryImpl.java
git commit -m "feat: PolicyProcessingStepRepository 추가 (port + JPA + impl)"
```

---

## Task 4: `PolicyProcessingStepService` 헬퍼 + 단위 테스트

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/application/service/PolicyProcessingStepService.java`
- Test: `backend/src/test/java/com/youthfit/policy/application/service/PolicyProcessingStepServiceTest.java`

`markStarted(policyId, step) → stepId` 와 `markFinished(stepId, status, reason, detailJson)` 두 메서드. attempt 자동 계산 (기존 attempt 수 + 1).

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PolicyProcessingStepServiceTest {

    private PolicyProcessingStepRepository repository;
    private PolicyProcessingStepService service;

    @BeforeEach
    void setUp() {
        repository = mock(PolicyProcessingStepRepository.class);
        service = new PolicyProcessingStepService(repository);
    }

    @Test
    void markStarted_는_attempt_1_로_저장한다_기존_없을_때() {
        when(repository.countByPolicyIdAndStep(1L, ProcessingStep.GUIDE)).thenReturn(0);
        when(repository.save(any())).thenAnswer(invocation -> {
            PolicyProcessingStep saved = invocation.getArgument(0);
            return saved;
        });

        Long stepId = service.markStarted(1L, ProcessingStep.GUIDE);

        ArgumentCaptor<PolicyProcessingStep> captor = ArgumentCaptor.forClass(PolicyProcessingStep.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(ProcessingStatus.IN_PROGRESS);
    }

    @Test
    void markStarted_는_attempt_N_plus_1_로_저장한다_기존_있을_때() {
        when(repository.countByPolicyIdAndStep(1L, ProcessingStep.RAG_INDEXING)).thenReturn(2);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.markStarted(1L, ProcessingStep.RAG_INDEXING);

        ArgumentCaptor<PolicyProcessingStep> captor = ArgumentCaptor.forClass(PolicyProcessingStep.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isEqualTo(3);
    }

    @Test
    void markFinished_는_엔티티_finish_호출_후_save_한다() {
        PolicyProcessingStep existing = PolicyProcessingStep.start(1L, ProcessingStep.GUIDE, 1);
        when(repository.findById(100L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.markFinished(100L, ProcessingStatus.SUCCESS, null, null);

        ArgumentCaptor<PolicyProcessingStep> captor = ArgumentCaptor.forClass(PolicyProcessingStep.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(captor.getValue().getFinishedAt()).isNotNull();
    }

    @Test
    void markFinished_는_없는_id_면_경고만_남기고_종료된다() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        service.markFinished(999L, ProcessingStatus.FAILED, "no row", null);

        verify(repository, never()).save(any());
    }
}
```

- [ ] **Step 2: 테스트 실행해서 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.PolicyProcessingStepServiceTest" -q`
Expected: FAIL — `PolicyProcessingStepService` 클래스 없음.

- [ ] **Step 3: 서비스 구현**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyProcessingStepService {

    private static final Logger log = LoggerFactory.getLogger(PolicyProcessingStepService.class);

    private final PolicyProcessingStepRepository repository;

    /**
     * 단계 시작 기록. attempt 는 기존 행 수 + 1 로 자동 계산.
     * @return 저장된 step row id (markFinished 호출 시 사용)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long markStarted(Long policyId, ProcessingStep step) {
        int attempt = repository.countByPolicyIdAndStep(policyId, step) + 1;
        PolicyProcessingStep row = PolicyProcessingStep.start(policyId, step, attempt);
        PolicyProcessingStep saved = repository.save(row);
        return saved.getId();
    }

    /**
     * 단계 종료 기록. 없는 id 면 경고 후 종료 (호출자 코드 안전성 우선).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFinished(Long stepRowId, ProcessingStatus status, String reason, String detailJson) {
        repository.findById(stepRowId).ifPresentOrElse(
                row -> {
                    row.finish(status, reason, detailJson);
                    repository.save(row);
                },
                () -> log.warn("processing step row 없음, finish 무시: rowId={}, status={}", stepRowId, status)
        );
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.PolicyProcessingStepServiceTest" -q`
Expected: PASS — 4 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/service/PolicyProcessingStepService.java backend/src/test/java/com/youthfit/policy/application/service/PolicyProcessingStepServiceTest.java
git commit -m "feat: PolicyProcessingStepService 추가 (markStarted, markFinished)"
```

---

## Task 5: `IngestionService` 에 INGESTION + ENRICHMENT step 기록 통합

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServicePolicyProcessingStepTest.java`

receivePolicy() 시작·종료 시 INGESTION step 기록 + payload 의 enrichment 가 있으면 그 status 그대로 ENRICHMENT step 기록.

- [ ] **Step 1: 기존 IngestionService 메서드 시그니처 확인**

Run: `grep -n "public.*receivePolicy\|class IngestionService" backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`
Expected: receivePolicy(IngestPolicyCommand command) 메서드 확인. 반환 타입과 매개변수 기록.

- [ ] **Step 2: 슬라이스 테스트 작성**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class IngestionServicePolicyProcessingStepTest {

    @Autowired private IngestionService ingestionService;
    @SpyBean private PolicyProcessingStepService stepService;

    @Test
    void receivePolicy_는_INGESTION_step_을_시작과_종료로_기록한다() {
        var command = TestFixtures.minimalCommand();

        ingestionService.receivePolicy(command);

        verify(stepService).markStarted(anyLong(), eq(ProcessingStep.INGESTION));
        verify(stepService).markFinished(anyLong(), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    void receivePolicy_는_enrichment_payload_가_있으면_ENRICHMENT_step_을_기록한다() {
        var command = TestFixtures.commandWithEnrichmentStatus("SUCCESS");

        ingestionService.receivePolicy(command);

        verify(stepService).markStarted(anyLong(), eq(ProcessingStep.ENRICHMENT));
        verify(stepService).markFinished(anyLong(), eq(ProcessingStatus.SUCCESS), any(), any());
    }

    @Test
    void receivePolicy_는_enrichment_status_SKIPPED_를_그대로_복사한다() {
        var command = TestFixtures.commandWithEnrichmentStatus("SKIPPED");

        ingestionService.receivePolicy(command);

        verify(stepService).markStarted(anyLong(), eq(ProcessingStep.ENRICHMENT));
        verify(stepService).markFinished(anyLong(), eq(ProcessingStatus.SKIPPED), any(), any());
    }
}
```

**참고**: `TestFixtures.minimalCommand()` 와 `commandWithEnrichmentStatus()` 는 본 task 안에서 같이 만든다 (아래 Step 3 에서 클래스 별도 파일로 작성).

- [ ] **Step 3: `TestFixtures` 헬퍼 클래스 작성**

`backend/src/test/java/com/youthfit/ingestion/application/service/TestFixtures.java`:

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.policy.domain.model.EnrichmentStatus;

import java.time.Instant;
import java.util.List;

class TestFixtures {

    static IngestPolicyCommand minimalCommand() {
        return new IngestPolicyCommand(
                "https://example.com/policy/1",
                "YOUTH_SEOUL",
                java.time.LocalDateTime.now(),
                "ext-1",
                "테스트 정책",
                "요약",
                "본문",
                "복지",
                "서울",
                null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null,
                null,
                null
        );
    }

    static IngestPolicyCommand commandWithEnrichmentStatus(String status) {
        return new IngestPolicyCommand(
                "https://example.com/policy/1",
                "YOUTH_SEOUL",
                java.time.LocalDateTime.now(),
                "ext-1",
                "테스트 정책",
                "요약",
                "본문",
                "복지",
                "서울",
                null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null,
                null,
                new PolicyEnrichment(
                        "https://ref.example.com",
                        Instant.now(),
                        "test-extractor",
                        0.85,
                        EnrichmentStatus.valueOf(status),
                        null,
                        List.of(),
                        "cleaned"
                )
        );
    }
}
```

**참고**: `IngestPolicyCommand` 의 정확한 파라미터 순서/타입은 `backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java` 를 먼저 읽고 동기화. 위 코드는 시그니처가 변경됐다면 컴파일 에러로 알려준다.

- [ ] **Step 4: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.IngestionServicePolicyProcessingStepTest" -q`
Expected: FAIL — `stepService` 가 호출되지 않음 (구현 아직 추가 안 됨).

- [ ] **Step 5: `IngestionService` 수정**

`IngestionService` 클래스에 `PolicyProcessingStepService stepService` 의존성 추가. `receivePolicy` 메서드 안의 정책 upsert 직후 다음 패턴 추가:

```java
// 정책 upsert (기존 로직) ...
Long policyId = registeredPolicy.getId();

Long ingestionStepId = stepService.markStarted(policyId, ProcessingStep.INGESTION);
try {
    // 이미 끝난 정규화·dedup 결과를 SUCCESS/SKIPPED 로 마킹
    if (outcome == Outcome.SKIPPED_DUPLICATE) {
        stepService.markFinished(ingestionStepId, ProcessingStatus.SKIPPED, "DUPLICATE", null);
    } else {
        stepService.markFinished(ingestionStepId, ProcessingStatus.SUCCESS, null, null);
    }
} catch (Exception e) {
    stepService.markFinished(ingestionStepId, ProcessingStatus.FAILED, summarize(e), null);
    throw e;
}

// enrichment payload 처리
PolicyEnrichment enrichment = command.enrichment();
if (enrichment != null) {
    Long enrichmentStepId = stepService.markStarted(policyId, ProcessingStep.ENRICHMENT);
    ProcessingStatus mappedStatus = mapEnrichmentStatus(enrichment.status());
    String detail = serializeEnrichmentDetail(enrichment);
    stepService.markFinished(enrichmentStepId, mappedStatus, enrichment.status().name(), detail);
}
```

추가 헬퍼 메서드 (같은 클래스 안):

```java
private ProcessingStatus mapEnrichmentStatus(EnrichmentStatus status) {
    return switch (status) {
        case SUCCESS -> ProcessingStatus.SUCCESS;
        case SKIPPED, NO_LINK -> ProcessingStatus.SKIPPED;
        case FAILED -> ProcessingStatus.FAILED;
    };
}

private String serializeEnrichmentDetail(PolicyEnrichment enrichment) {
    // detail_json 에 extractor + skippedUrls 직렬화. ObjectMapper bean 사용.
    try {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "extractor", enrichment.extractor(),
                "confidence", enrichment.confidence(),
                "sourceUrl", enrichment.sourceUrl()
        ));
    } catch (Exception e) {
        return null;
    }
}

private String summarize(Throwable e) {
    return e.getClass().getSimpleName() + ": " + e.getMessage();
}
```

**참고**: `EnrichmentStatus` enum 값은 `backend/src/main/java/com/youthfit/policy/domain/model/EnrichmentStatus.java` 를 먼저 읽고 케이스 정확히 동기화. `NO_LINK` 가 없다면 분기에서 제외.

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.IngestionServicePolicyProcessingStepTest" -q`
Expected: PASS — 3 테스트 통과.

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServicePolicyProcessingStepTest.java backend/src/test/java/com/youthfit/ingestion/application/service/TestFixtures.java
git commit -m "feat: IngestionService 에 INGESTION + ENRICHMENT step 기록 추가"
```

---

## Task 6: `GuideGenerationEventListener` 에 GUIDE step 기록 통합

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/listener/GuideGenerationEventListener.java`
- Test: `backend/src/test/java/com/youthfit/guide/application/listener/GuideGenerationEventListenerStepTest.java`

기존 onPolicyUpserted 메서드의 try-catch 를 step 기록으로 감싼다.

- [ ] **Step 1: 테스트 작성**

```java
package com.youthfit.guide.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuideGenerationEventListenerStepTest {

    private GuideGenerationService guideService;
    private PolicyProcessingStepService stepService;
    private GuideGenerationEventListener listener;

    @BeforeEach
    void setUp() {
        guideService = mock(GuideGenerationService.class);
        stepService = mock(PolicyProcessingStepService.class);
        listener = new GuideGenerationEventListener(guideService, stepService);
    }

    @Test
    void onPolicyUpserted_성공_시_GUIDE_SUCCESS_기록() {
        when(stepService.markStarted(1L, ProcessingStep.GUIDE)).thenReturn(100L);

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트", null));

        verify(stepService).markStarted(1L, ProcessingStep.GUIDE);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    void onPolicyUpserted_예외_시_GUIDE_FAILED_기록_후_swallow() {
        when(stepService.markStarted(1L, ProcessingStep.GUIDE)).thenReturn(100L);
        doThrow(new RuntimeException("LLM 실패")).when(guideService).generateGuide(any());

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트", null));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.FAILED), reasonCaptor.capture(), isNull());
        org.assertj.core.api.Assertions.assertThat(reasonCaptor.getValue()).contains("LLM 실패");
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.listener.GuideGenerationEventListenerStepTest" -q`
Expected: FAIL — 생성자가 인자 1개 (`guideService`) 만 받는다 / `stepService` 호출 0회.

- [ ] **Step 3: `GuideGenerationEventListener` 수정**

기존 `onPolicyUpserted` 메서드 (라인 ~25-32) 를 다음으로 교체:

```java
@Async("llmExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void onPolicyUpserted(PolicyUpsertedEvent event) {
    Long stepId = stepService.markStarted(event.policyId(), ProcessingStep.GUIDE);
    try {
        guideGenerationService.generateGuide(
                new GenerateGuideCommand(event.policyId(), event.title(), null));
        stepService.markFinished(stepId, ProcessingStatus.SUCCESS, null, null);
    } catch (Exception e) {
        stepService.markFinished(stepId, ProcessingStatus.FAILED, summarize(e), null);
        log.warn("가이드 생성 실패 (event=PolicyUpsertedEvent): policyId={}", event.policyId(), e);
    }
}
```

`onAttachmentReindexed` 메서드도 동일 패턴 적용:

```java
@Async("llmExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void onAttachmentReindexed(PolicyAttachmentReindexedEvent event) {
    Long stepId = stepService.markStarted(event.policyId(), ProcessingStep.GUIDE);
    try {
        guideGenerationService.generateGuide(
                new GenerateGuideCommand(event.policyId(), null, null));
        stepService.markFinished(stepId, ProcessingStatus.SUCCESS, null, null);
    } catch (Exception e) {
        stepService.markFinished(stepId, ProcessingStatus.FAILED, summarize(e), null);
        log.warn("가이드 재생성 실패 (event=PolicyAttachmentReindexedEvent): policyId={}", event.policyId(), e);
    }
}

private static String summarize(Throwable t) {
    return t.getClass().getSimpleName() + ": " + t.getMessage();
}
```

클래스 필드에 `PolicyProcessingStepService stepService` 추가:

```java
private final GuideGenerationService guideGenerationService;
private final PolicyProcessingStepService stepService;  // ★ 추가
```

import:

```java
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.listener.GuideGenerationEventListenerStepTest" -q`
Expected: PASS — 2 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/listener/GuideGenerationEventListener.java backend/src/test/java/com/youthfit/guide/application/listener/GuideGenerationEventListenerStepTest.java
git commit -m "feat: GuideGenerationEventListener 에 GUIDE step 기록 추가"
```

---

## Task 7: `EligibilityRuleGenerationEventListener` 에 RULE step 기록 통합

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListener.java`
- Test: `backend/src/test/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListenerStepTest.java`

Task 6 과 동일 패턴.

- [ ] **Step 1: 테스트 작성**

```java
package com.youthfit.eligibility.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EligibilityRuleGenerationEventListenerStepTest {

    private EligibilityRuleGenerationService ruleService;
    private PolicyProcessingStepService stepService;
    private EligibilityRuleGenerationEventListener listener;

    @BeforeEach
    void setUp() {
        ruleService = mock(EligibilityRuleGenerationService.class);
        stepService = mock(PolicyProcessingStepService.class);
        listener = new EligibilityRuleGenerationEventListener(ruleService, stepService);
    }

    @Test
    void onPolicyUpserted_성공_시_RULE_SUCCESS_기록() {
        when(stepService.markStarted(1L, ProcessingStep.RULE)).thenReturn(100L);

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트", null));

        verify(stepService).markStarted(1L, ProcessingStep.RULE);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    void onPolicyUpserted_예외_시_RULE_FAILED_기록_후_swallow() {
        when(stepService.markStarted(1L, ProcessingStep.RULE)).thenReturn(100L);
        doThrow(new RuntimeException("rule 추출 실패")).when(ruleService).generateRules(any());

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트", null));

        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.FAILED), contains("rule 추출 실패"), isNull());
    }
}
```

**참고**: `generateRules` 메서드명은 실제 `EligibilityRuleGenerationService` 와 동기화. 다르면 컴파일 에러로 알려준다.

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.eligibility.application.listener.EligibilityRuleGenerationEventListenerStepTest" -q`
Expected: FAIL — `stepService` 호출 안 됨.

- [ ] **Step 3: listener 수정**

기존 `onPolicyUpserted` 메서드를 다음으로 감싼다:

```java
@Async("llmExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void onPolicyUpserted(PolicyUpsertedEvent event) {
    Long stepId = stepService.markStarted(event.policyId(), ProcessingStep.RULE);
    try {
        // 기존 룰 추출 로직 그대로
        ruleGenerationService.generateRules(/* 기존 command */);
        stepService.markFinished(stepId, ProcessingStatus.SUCCESS, null, null);
    } catch (Exception e) {
        stepService.markFinished(stepId, ProcessingStatus.FAILED, summarize(e), null);
        log.warn("룰 추출 실패: policyId={}", event.policyId(), e);
    }
}

private static String summarize(Throwable t) {
    return t.getClass().getSimpleName() + ": " + t.getMessage();
}
```

클래스 필드 추가:
```java
private final PolicyProcessingStepService stepService;
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.eligibility.application.listener.EligibilityRuleGenerationEventListenerStepTest" -q`
Expected: PASS — 2 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListener.java backend/src/test/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListenerStepTest.java
git commit -m "feat: EligibilityRuleGenerationEventListener 에 RULE step 기록 추가"
```

---

## Task 8: `RagIndexingEventListener` 에 RAG_INDEXING step 기록 + #126 회귀 테스트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/application/listener/RagIndexingEventListener.java`
- Test: `backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingEventListenerStepTest.java`
- Test: `backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingRegressionTest.java` (#126 회귀)

본 Task 가 Phase A 의 핵심 — #126 (RAG listener 미동작) 회귀 방지 테스트가 들어간다.

- [ ] **Step 1: 단위 테스트 작성**

```java
package com.youthfit.rag.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagIndexingEventListenerStepTest {

    private PolicyRepository policyRepository;
    private RagIndexingService ragIndexingService;
    private PolicyProcessingStepService stepService;
    private RagIndexingEventListener listener;

    @BeforeEach
    void setUp() {
        policyRepository = mock(PolicyRepository.class);
        ragIndexingService = mock(RagIndexingService.class);
        stepService = mock(PolicyProcessingStepService.class);
        listener = new RagIndexingEventListener(policyRepository, ragIndexingService, stepService);
    }

    @Test
    void onPolicyUpserted_성공_시_RAG_INDEXING_SUCCESS_기록() {
        Policy policy = mock(Policy.class);
        when(policy.getBody()).thenReturn("정책 본문");
        when(policy.getEnrichment()).thenReturn(null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(stepService.markStarted(1L, ProcessingStep.RAG_INDEXING)).thenReturn(100L);
        when(ragIndexingService.indexPolicyDocument(any())).thenReturn(new IndexingResult(1L, 5, true));

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트", null));

        verify(stepService).markStarted(1L, ProcessingStep.RAG_INDEXING);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    void onPolicyUpserted_본문_없으면_RAG_INDEXING_SKIPPED_기록() {
        Policy policy = mock(Policy.class);
        when(policy.getBody()).thenReturn("");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(stepService.markStarted(1L, ProcessingStep.RAG_INDEXING)).thenReturn(100L);

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트", null));

        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SKIPPED), eq("EMPTY_BODY"), isNull());
        verify(ragIndexingService, never()).indexPolicyDocument(any());
    }

    @Test
    void onPolicyUpserted_예외_시_RAG_INDEXING_FAILED_기록_후_swallow() {
        Policy policy = mock(Policy.class);
        when(policy.getBody()).thenReturn("본문");
        when(policy.getEnrichment()).thenReturn(null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(stepService.markStarted(1L, ProcessingStep.RAG_INDEXING)).thenReturn(100L);
        when(ragIndexingService.indexPolicyDocument(any())).thenThrow(new RuntimeException("embedding 실패"));

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트", null));

        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.FAILED), contains("embedding 실패"), isNull());
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.application.listener.RagIndexingEventListenerStepTest" -q`
Expected: FAIL — 생성자가 인자 2개만 받음.

- [ ] **Step 3: `RagIndexingEventListener` 수정**

기존 `onPolicyUpserted` 메서드 (라인 29-48) 를 다음으로 교체:

```java
@Async("llmExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void onPolicyUpserted(PolicyUpsertedEvent event) {
    Long stepId = stepService.markStarted(event.policyId(), ProcessingStep.RAG_INDEXING);
    try {
        Optional<Policy> policyOpt = policyRepository.findById(event.policyId());
        if (policyOpt.isEmpty()) {
            stepService.markFinished(stepId, ProcessingStatus.SKIPPED, "POLICY_NOT_FOUND", null);
            log.warn("정책 미존재 — RAG 1차 인덱싱 스킵: policyId={}", event.policyId());
            return;
        }
        Policy policy = policyOpt.get();
        String body = policy.getBody();
        if (body == null || body.isBlank()) {
            stepService.markFinished(stepId, ProcessingStatus.SKIPPED, "EMPTY_BODY", null);
            log.info("정책 본문 비어 있음 — RAG 1차 인덱싱 스킵: policyId={}", event.policyId());
            return;
        }
        ragIndexingService.indexPolicyDocument(
                new IndexPolicyDocumentCommand(event.policyId(), body, policy.getEnrichment()));
        stepService.markFinished(stepId, ProcessingStatus.SUCCESS, null, null);
    } catch (Exception e) {
        stepService.markFinished(stepId, ProcessingStatus.FAILED, summarize(e), null);
        log.warn("RAG 1차 인덱싱 실패 (event=PolicyUpsertedEvent): policyId={}",
                event.policyId(), e);
    }
}

private static String summarize(Throwable t) {
    return t.getClass().getSimpleName() + ": " + t.getMessage();
}
```

생성자 추가 의존성 `PolicyProcessingStepService stepService`:

```java
private final PolicyRepository policyRepository;
private final RagIndexingService ragIndexingService;
private final PolicyProcessingStepService stepService;  // ★ 추가
```

import:
```java
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
```

- [ ] **Step 4: 단위 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.application.listener.RagIndexingEventListenerStepTest" -q`
Expected: PASS — 3 테스트 통과.

- [ ] **Step 5: #126 회귀 통합 테스트 작성**

```java
package com.youthfit.rag.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class RagIndexingRegressionTest {

    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private PolicyProcessingStepRepository stepRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private TestPolicyFactory policyFactory;

    /**
     * #126 회귀 — RAG indexing listener 가 PolicyUpsertedEvent 에 정상 반응해서
     * RAG_INDEXING step 이 기록되는지 보장.
     */
    @Test
    void RAG_indexing_listener_가_PolicyUpsertedEvent_에_정상_반응한다() {
        Long policyId = policyFactory.createPolicyWithBody("회귀 테스트 정책");

        txTemplate.execute(status -> {
            publisher.publishEvent(new PolicyUpsertedEvent(policyId, "회귀 테스트", null));
            return null;
        });

        await().atMost(Duration.ofSeconds(10)).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            var stepOpt = stepRepository.findLatestByPolicyIdAndStep(policyId, ProcessingStep.RAG_INDEXING);
            assertThat(stepOpt).isPresent();
            assertThat(stepOpt.get().getStatus())
                    .isIn(ProcessingStatus.SUCCESS, ProcessingStatus.FAILED, ProcessingStatus.SKIPPED);
            // 핵심: IN_PROGRESS 가 아니어야 함 = listener 가 fire 됐고 markFinished 호출됨
        });
    }
}
```

**참고**: `TestPolicyFactory` 가 없다면 같은 패키지에 만든다 — 실제 PolicyRepository 와 IngestionService 를 사용해 정책 1건 적재하는 헬퍼. 또는 기존 헬퍼 (`@Sql` 등) 사용. 통합 테스트 환경의 결정은 `backend/src/test/java/com/youthfit/rag/` 의 기존 통합 테스트 파일을 참조해서 패턴 통일.

`await()` 는 awaitility 라이브러리 — 이미 backend `build.gradle.kts` 의 test dependencies 에 있을 가능성. 없으면 추가:

```kotlin
testImplementation("org.awaitility:awaitility:4.2.0")
```

- [ ] **Step 6: 회귀 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.application.listener.RagIndexingRegressionTest" -q`
Expected: PASS — RAG_INDEXING step 이 IN_PROGRESS 가 아닌 종결 상태로 기록됨.

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/application/listener/RagIndexingEventListener.java backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingEventListenerStepTest.java backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingRegressionTest.java
git commit -m "feat: RagIndexingEventListener 에 RAG_INDEXING step 기록 + #126 회귀 테스트"
```

---

## Task 9: e2e 통합 테스트 (전체 5단계 흐름)

**Files:**
- Test: `backend/src/test/java/com/youthfit/policy/application/service/PolicyProcessingStepE2ETest.java`

청년몽땅 샘플 payload 1건을 ingest → 5분 안에 5개 step 행이 모두 종결 상태로 기록되는지 확인.

- [ ] **Step 1: e2e 테스트 작성**

```java
package com.youthfit.policy.application.service;

import com.youthfit.ingestion.application.service.IngestionService;
import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class PolicyProcessingStepE2ETest {

    @Autowired private IngestionService ingestionService;
    @Autowired private PolicyProcessingStepRepository stepRepository;

    @Test
    void 정책_적재_후_5단계_step_이_모두_기록된다() {
        var command = com.youthfit.ingestion.application.service.TestFixtures.commandWithEnrichmentStatus("SUCCESS");

        var result = ingestionService.receivePolicy(command);
        Long policyId = result.policyId();

        await().atMost(Duration.ofSeconds(60)).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            List<PolicyProcessingStep> steps = stepRepository.findByPolicyIdOrderByStep(policyId);
            Map<ProcessingStep, ProcessingStatus> latest = new java.util.EnumMap<>(ProcessingStep.class);
            for (PolicyProcessingStep s : steps) {
                latest.merge(s.getStep(), s.getStatus(),
                        (a, b) -> b);   // 같은 step 다중 attempt 시 마지막 status 만 보관
            }
            assertThat(latest).containsKeys(
                    ProcessingStep.INGESTION,
                    ProcessingStep.ENRICHMENT,
                    ProcessingStep.GUIDE,
                    ProcessingStep.RULE,
                    ProcessingStep.RAG_INDEXING
            );
            assertThat(latest.values()).noneMatch(s -> s == ProcessingStatus.IN_PROGRESS);
        });
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.PolicyProcessingStepE2ETest" -q`
Expected: PASS — 5단계 모두 종결 상태.

**주의**: OpenAI 외부 호출이 일어나는 통합 테스트라 시간 소요. CI 환경에서는 별도 tag 처리 검토:
```java
@Test
@org.junit.jupiter.api.Tag("integration")
```
별도 gradle task 로 분리해서 평소 빌드에서는 skip 도 한 가지 옵션.

- [ ] **Step 3: 전체 backend 테스트 회귀 확인**

Run: `cd backend && ./gradlew test -q 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL — Phase A 추가가 기존 테스트 어디도 깨뜨리지 않음.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/test/java/com/youthfit/policy/application/service/PolicyProcessingStepE2ETest.java
git commit -m "test: 정책 5단계 step 기록 e2e 통합 테스트"
```

---

## Phase A 완료 후 정리

- [ ] **Step 1: 변경 요약 확인**

Run: `git log --oneline 3391dde..HEAD`
Expected: 8개 커밋 (Task 1~9). spec 커밋 + Phase A 의 각 task 별 커밋.

- [ ] **Step 2: 로컬에서 실제 정책 1건 ingest 후 step 행 확인**

```bash
docker compose up -d
# (백엔드 부팅 대기)
# n8n UI 또는 기존 sample payload 로 정책 1건 ingest

docker compose exec -T postgres psql -U youthfit -d youthfit -c "
SELECT step, status, attempt, reason, duration_ms
FROM policy_processing_step
WHERE policy_id = (SELECT max(id) FROM policy)
ORDER BY step, attempt;
"
```

Expected: 5행 (INGESTION/ENRICHMENT/GUIDE/RULE/RAG_INDEXING), 모두 SUCCESS 또는 SKIPPED.

- [ ] **Step 3: PR 작성 (`/create-pr` 또는 수동)**

PR 제목: `feat(be): 정책 처리 5단계 추적 인프라 (Phase A)`

PR 본문 키 포인트:
- 신규 테이블 `policy_processing_step` (JPA Entity 로 자동 생성)
- 5개 진입점 (Ingestion + 3 listener) 가 step 기록
- #126 회귀 테스트 포함
- 사용자 노출에 영향 없음 (관측 인프라 only)

## Self-Review

본 plan 자체에 대한 self-check (작성자가 spec 과 대조해 빠진 게 있는지 확인):

**Spec 커버리지**:
- spec §5-1 (`policy_processing_step` 테이블 정의) → Task 2 (엔티티 + @Index/@UniqueConstraint 로 SQL 동등 구현) ✅
- spec §5-2 (enum 정의) → Task 1 ✅
- spec §5-3 (`policy.processing_status` 컬럼) → ❌ Phase B 의 일이라 본 Phase 에 없음 (의도)
- spec §5-4 (마이그레이션) → JPA `ddl-auto=update` 가 처리. 별도 V*.sql 파일 불필요 (memory: 본 프로젝트는 init SQL 보조 패턴)
- spec §5-5 (EnrichmentPayload.skippedUrls) → ❌ Phase D (n8n) 와 함께 변경. 본 Phase 의 Task 5 는 기존 EnrichmentPayload 그대로 사용
- spec §4-2 (백엔드 step 기록 시점) → Task 5/6/7/8 ✅
- spec §6-1 (실패 시나리오 매트릭스) → Task 5/6/7/8 의 catch + Task 8 의 EMPTY_BODY SKIPPED ✅
- spec §7-1 (#126 회귀 테스트) → Task 8 ✅

**Placeholder 검사**: TBD / TODO / "implement later" 없음 ✅

**Type 일관성**:
- `markStarted(Long policyId, ProcessingStep step) -> Long stepId` — Task 4 에서 정의, Task 5/6/7/8 에서 동일 시그니처 사용 ✅
- `markFinished(Long stepRowId, ProcessingStatus status, String reason, String detailJson)` — Task 4 정의, Task 5/6/7/8 동일 사용 ✅
- `PolicyProcessingStep.start(policyId, step, attempt)` — Task 2 정의, Task 4 의 markStarted 안에서 사용 ✅
- `PolicyProcessingStep.finish(status, reason, detailJson)` — Task 2 정의, Task 4 의 markFinished 안에서 사용 ✅

**잠재적 이슈**:
- `IngestPolicyCommand` 의 record 필드 개수가 매우 많아 (>30) `TestFixtures.minimalCommand()` 가 컴파일 안 될 수 있음 — Task 5 Step 3 주석에 명시한 대로 실제 record 시그니처 먼저 읽고 동기화 필요.
- `EligibilityRuleGenerationService.generateRules(...)` 메서드명 — Task 7 Step 1 주석에 동기화 명시.
- `awaitility` 의존성 — Task 8 Step 5 에서 build.gradle 확인 명시.
- `@SpringBootTest` 환경에서 OpenAI 외부 호출이 실제로 일어남 — Task 9 의 e2e 테스트는 CI 부담. tag 처리 옵션 명시.
