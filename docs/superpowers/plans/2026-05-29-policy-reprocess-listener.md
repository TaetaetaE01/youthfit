# PolicyReprocessRequestedEvent Listener Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PR #127 의 "전체 재처리" 버튼이 실제로 4단계 (ENRICHMENT/GUIDE/RULE/RAG_INDEXING) 를 실행하도록 listener 를 구현하고, listener 누락/장애로 IN_PROGRESS 로 굳은 step 행을 자동 정리하는 timeout scheduler 를 추가한다.

**Architecture:** `admin.application.listener.PolicyReprocessRequestedEventListener` 가 `@Async("llmExecutor")` 로 이벤트를 받아 ENRICHMENT 는 SKIPPED 마감, 나머지 3단계는 `retryStep` 의 호출 패턴 (Command DTO) 그대로 실행. 각 단계 독립 try/catch 로 단계 실패 격리. 별도로 `PolicyProcessingStepTimeoutScheduler` 가 1분 주기로 status=IN_PROGRESS && started_at < now-10min 인 행을 FAILED 로 일괄 정리.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Spring `@Async` + `@Scheduled`, JUnit 5 + Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-05-29-policy-reprocess-listener-design.md`

---

## File Structure

**Create:**
- `backend/src/main/java/com/youthfit/admin/application/listener/PolicyReprocessRequestedEventListener.java` — 이벤트 수신 + 4단계 실행
- `backend/src/main/java/com/youthfit/policy/infrastructure/scheduler/PolicyProcessingStepTimeoutScheduler.java` — stale 행 정리
- `backend/src/test/java/com/youthfit/admin/application/listener/PolicyReprocessRequestedEventListenerTest.java` — listener 단위 테스트
- `backend/src/test/java/com/youthfit/policy/infrastructure/scheduler/PolicyProcessingStepTimeoutSchedulerTest.java` — scheduler 단위 테스트
- `backend/src/test/java/com/youthfit/admin/integration/PolicyReprocessIntegrationTest.java` — 통합 테스트

**Modify:**
- `backend/src/main/java/com/youthfit/policy/domain/model/PolicyProcessingStep.java` — `markTimedOut()` 도메인 메서드 추가
- `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyProcessingStepRepository.java` — `findActiveStaleBefore(Instant)` 메서드 추가
- `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepJpaRepository.java` — `findByStatusAndStartedAtBefore` 메서드 추가
- `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepRepositoryImpl.java` — 새 메서드 위임

---

## Phase 1 — Listener 구현

### Task 1: Listener happy path 단위 테스트 (failing test)

**Files:**
- Create: `backend/src/test/java/com/youthfit/admin/application/listener/PolicyReprocessRequestedEventListenerTest.java`

- [ ] **Step 1: Test 파일 작성 (happy path 1건만)**

```java
package com.youthfit.admin.application.listener;

import com.youthfit.common.event.PolicyReprocessRequestedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.service.RagIndexingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("PolicyReprocessRequestedEventListener")
@ExtendWith(MockitoExtension.class)
class PolicyReprocessRequestedEventListenerTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyProcessingStepService stepService;
    @Mock private RagIndexingService ragIndexingService;
    @Mock private GuideGenerationService guideGenerationService;
    @Mock private EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @InjectMocks
    private PolicyReprocessRequestedEventListener listener;

    @Test
    @DisplayName("정책 존재 시 ENRICHMENT(SKIPPED) → GUIDE → RULE → RAG 순으로 호출하고 stepIds 를 순서대로 마감한다")
    void onPolicyReprocessRequested_executesAllFourStepsInOrder() {
        // given
        Policy policy = mock(Policy.class);
        given(policy.getId()).willReturn(100L);
        given(policy.getTitle()).willReturn("청년월세 지원");
        given(policy.getBody()).willReturn("본문");
        given(policy.getEnrichment()).willReturn(null);
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));

        List<Long> stepIds = List.of(11L, 12L, 13L, 14L);
        PolicyReprocessRequestedEvent event = new PolicyReprocessRequestedEvent(100L, "운영자 요청", stepIds);

        // when
        listener.onPolicyReprocessRequested(event);

        // then — InOrder 로 호출 순서 검증
        InOrder order = inOrder(stepService, guideGenerationService, eligibilityRuleGenerationService, ragIndexingService);
        order.verify(stepService).markFinished(eq(11L), eq(ProcessingStatus.SKIPPED), any(), any());
        order.verify(guideGenerationService).generateGuide(any(GenerateGuideCommand.class));
        order.verify(stepService).markFinished(eq(12L), eq(ProcessingStatus.SUCCESS), any(), any());
        order.verify(eligibilityRuleGenerationService).generateRules(any(GenerateEligibilityRulesCommand.class));
        order.verify(stepService).markFinished(eq(13L), eq(ProcessingStatus.SUCCESS), any(), any());
        order.verify(ragIndexingService).indexPolicyDocument(any(IndexPolicyDocumentCommand.class));
        order.verify(stepService).markFinished(eq(14L), eq(ProcessingStatus.SUCCESS), any(), any());
    }

    private static <T> T mock(Class<T> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }
}
```

> Note: `Policy` 는 final entity 라 `Mockito.mock(Policy.class)` 가 mockito-inline 없이는 동작 안 할 수 있다. `Policy` 가 final 인지 step 2 에서 컴파일/실행으로 확인. 만약 final 이면 step 2 에서 mocking 대신 reflection 또는 builder 로 생성하는 방향으로 수정.

- [ ] **Step 2: 테스트 컴파일 / 실행 → 실패 확인**

Run: `cd backend && ./gradlew test --tests "PolicyReprocessRequestedEventListenerTest" -q`
Expected: FAIL — `PolicyReprocessRequestedEventListener` 클래스 미존재로 컴파일 실패. (`cannot find symbol class PolicyReprocessRequestedEventListener`)

만약 `Policy` mocking 이슈 발생: Policy 엔티티에 `@Setter` 없을 가능성 있음. test 안에서 mock 대신 `Policy` 객체 직접 생성하는 helper 가 필요한지 step 3 에서 결정.

> 만약 `Policy` 가 non-final 이고 mockito 가 기본 정상 동작하면 그대로 진행. final 이면: `backend/build.gradle` 의 mockito 설정 확인 후 mockito-inline 또는 `Policy` 의 정적 빌더 (`Policy.create(...)`) 활용 방향으로 helper 추가.

---

### Task 2: Listener 클래스 구현 (skeleton + 4단계)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/application/listener/PolicyReprocessRequestedEventListener.java`

- [ ] **Step 1: Listener 신규 파일 작성**

```java
package com.youthfit.admin.application.listener;

import com.youthfit.common.event.PolicyReprocessRequestedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.service.RagIndexingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 어드민 전체 재처리 이벤트 수신 listener.
 *
 * <p>{@link PolicyReprocessRequestedEvent} 를 받아 ENRICHMENT/GUIDE/RULE/RAG_INDEXING
 * 4 단계를 순차 실행하고 step 행을 마감한다. ENRICHMENT 는 MVP 에서 n8n 재크롤이 필요해
 * SKIPPED 로 처리 (admin {@code retryStep(ENRICHMENT)} 와 동일한 정책).</p>
 *
 * <p>호출 패턴은 {@code AdminPolicyProcessingService.retryStep} 의 단계별 호출 그대로 재사용.
 * stepIds 인덱스는 발행 측 ({@code AdminPolicyProcessingService.reprocess}) 가 코드 레벨에서
 * ENRICHMENT(0), GUIDE(1), RULE(2), RAG_INDEXING(3) 순서로 보장한다.</p>
 *
 * <p>비동기 실행은 기존 {@code llmExecutor} 빈 (core 2 / max 4 / queue 100 / CallerRunsPolicy)
 * 재사용. HTTP 응답이 LLM 호출 대기에 블록되지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class PolicyReprocessRequestedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PolicyReprocessRequestedEventListener.class);
    private static final String ENRICHMENT_SKIP_REASON = "MVP: ENRICHMENT manual trigger 미연결";
    private static final String POLICY_NOT_FOUND_REASON = "정책 없음";

    private final PolicyRepository policyRepository;
    private final PolicyProcessingStepService stepService;
    private final RagIndexingService ragIndexingService;
    private final GuideGenerationService guideGenerationService;
    private final EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @Async("llmExecutor")
    @EventListener
    public void onPolicyReprocessRequested(PolicyReprocessRequestedEvent event) {
        List<Long> ids = event.stepIds();
        Policy policy = policyRepository.findById(event.policyId()).orElse(null);
        if (policy == null) {
            log.warn("재처리 이벤트 도착 — 정책 없음: policyId={}", event.policyId());
            ids.forEach(id -> stepService.markFinished(id, ProcessingStatus.FAILED, POLICY_NOT_FOUND_REASON, null));
            return;
        }

        // 인덱스 매칭: [0]=ENRICHMENT, [1]=GUIDE, [2]=RULE, [3]=RAG_INDEXING
        stepService.markFinished(ids.get(0), ProcessingStatus.SKIPPED, ENRICHMENT_SKIP_REASON, null);

        runWithStep(ids.get(1), () -> guideGenerationService.generateGuide(
                new GenerateGuideCommand(policy.getId(), policy.getTitle(), null)));
        runWithStep(ids.get(2), () -> eligibilityRuleGenerationService.generateRules(
                new GenerateEligibilityRulesCommand(policy.getId())));
        runWithStep(ids.get(3), () -> ragIndexingService.indexPolicyDocument(
                new IndexPolicyDocumentCommand(policy.getId(), policy.getBody(), policy.getEnrichment())));
    }

    private void runWithStep(Long stepRowId, Runnable action) {
        try {
            action.run();
            stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS, null, null);
        } catch (Exception e) {
            log.warn("재처리 단계 실패: stepRowId={} message={}", stepRowId, e.getMessage());
            stepService.markFinished(stepRowId, ProcessingStatus.FAILED, e.getMessage(), null);
        }
    }
}
```

- [ ] **Step 2: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "PolicyReprocessRequestedEventListenerTest" -q`
Expected: PASS — happy path 1건이 InOrder 검증 통과.

> 만약 `Policy` mock 이슈로 fail: Policy 클래스 final 여부에 따라 helper 로 대체. 그 때만 Task 1 의 mock 호출을 builder/factory 로 교체.

- [ ] **Step 3: 컴파일 + 전체 빌드 안 깨졌는지 확인**

Run: `cd backend && ./gradlew compileJava compileTestJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/admin/application/listener/PolicyReprocessRequestedEventListener.java \
        backend/src/test/java/com/youthfit/admin/application/listener/PolicyReprocessRequestedEventListenerTest.java
git commit -m "$(cat <<'EOF'
feat(be): PolicyReprocessRequestedEvent listener 추가 (happy path)

@Async("llmExecutor") 로 4단계 (ENRICHMENT SKIPPED / GUIDE / RULE / RAG_INDEXING)
순차 실행, retryStep 패턴 재사용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Listener 엣지 케이스 테스트 (정책 없음 / 단계 실패 격리 / ENRICHMENT reason)

**Files:**
- Modify: `backend/src/test/java/com/youthfit/admin/application/listener/PolicyReprocessRequestedEventListenerTest.java`

- [ ] **Step 1: 정책 없음 케이스 테스트 추가**

기존 테스트 클래스에 다음 메서드 추가:

```java
    @Test
    @DisplayName("정책이 없으면 4개 stepIds 전부 FAILED 로 마감하고 service 호출 안 함")
    void onPolicyReprocessRequested_policyMissing_marksAllFailed() {
        // given
        given(policyRepository.findById(999L)).willReturn(Optional.empty());
        List<Long> stepIds = List.of(21L, 22L, 23L, 24L);
        PolicyReprocessRequestedEvent event = new PolicyReprocessRequestedEvent(999L, "사유", stepIds);

        // when
        listener.onPolicyReprocessRequested(event);

        // then
        verify(stepService).markFinished(eq(21L), eq(ProcessingStatus.FAILED), eq("정책 없음"), eq(null));
        verify(stepService).markFinished(eq(22L), eq(ProcessingStatus.FAILED), eq("정책 없음"), eq(null));
        verify(stepService).markFinished(eq(23L), eq(ProcessingStatus.FAILED), eq("정책 없음"), eq(null));
        verify(stepService).markFinished(eq(24L), eq(ProcessingStatus.FAILED), eq("정책 없음"), eq(null));
        verify(guideGenerationService, never()).generateGuide(any());
        verify(eligibilityRuleGenerationService, never()).generateRules(any());
        verify(ragIndexingService, never()).indexPolicyDocument(any());
    }
```

- [ ] **Step 2: GUIDE 실패 시 RULE/RAG 계속 진행 테스트 추가**

```java
    @Test
    @DisplayName("GUIDE 단계 실패해도 RULE/RAG 계속 진행하고, 실패한 step 만 FAILED 로 마감한다")
    void onPolicyReprocessRequested_guideFailure_doesNotBlockSubsequentSteps() {
        // given
        Policy policy = mock(Policy.class);
        given(policy.getId()).willReturn(100L);
        given(policy.getTitle()).willReturn("청년월세");
        given(policy.getBody()).willReturn("본문");
        given(policy.getEnrichment()).willReturn(null);
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));
        given(guideGenerationService.generateGuide(any()))
                .willThrow(new RuntimeException("LLM rate limit"));

        List<Long> stepIds = List.of(31L, 32L, 33L, 34L);
        PolicyReprocessRequestedEvent event = new PolicyReprocessRequestedEvent(100L, "사유", stepIds);

        // when
        listener.onPolicyReprocessRequested(event);

        // then
        verify(stepService).markFinished(eq(31L), eq(ProcessingStatus.SKIPPED), any(), any()); // ENRICHMENT
        verify(stepService).markFinished(eq(32L), eq(ProcessingStatus.FAILED), eq("LLM rate limit"), eq(null)); // GUIDE
        verify(eligibilityRuleGenerationService).generateRules(any()); // RULE 계속 호출
        verify(stepService).markFinished(eq(33L), eq(ProcessingStatus.SUCCESS), any(), any()); // RULE
        verify(ragIndexingService).indexPolicyDocument(any()); // RAG 계속 호출
        verify(stepService).markFinished(eq(34L), eq(ProcessingStatus.SUCCESS), any(), any()); // RAG
    }
```

- [ ] **Step 3: ENRICHMENT SKIPPED reason 검증 테스트 추가**

```java
    @Test
    @DisplayName("ENRICHMENT 단계는 SKIPPED 로 마감되고 reason 은 retryStep 과 동일한 메시지")
    void onPolicyReprocessRequested_enrichmentMarkedSkippedWithRetryStepMessage() {
        // given
        Policy policy = mock(Policy.class);
        given(policy.getId()).willReturn(100L);
        given(policy.getTitle()).willReturn("t");
        given(policy.getBody()).willReturn("b");
        given(policy.getEnrichment()).willReturn(null);
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));

        List<Long> stepIds = List.of(41L, 42L, 43L, 44L);
        PolicyReprocessRequestedEvent event = new PolicyReprocessRequestedEvent(100L, "사유", stepIds);

        // when
        listener.onPolicyReprocessRequested(event);

        // then — reason 메시지가 retryStep(ENRICHMENT) 와 정확히 일치하는지 검증
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(stepService).markFinished(eq(41L), eq(ProcessingStatus.SKIPPED), reason.capture(), eq(null));
        assertThat(reason.getValue()).isEqualTo("MVP: ENRICHMENT manual trigger 미연결");
    }
```

- [ ] **Step 4: 전체 테스트 실행 → 통과 확인**

Run: `cd backend && ./gradlew test --tests "PolicyReprocessRequestedEventListenerTest" -q`
Expected: PASS — 총 4건 (happy path + 3 엣지 케이스).

- [ ] **Step 5: 커밋**

```bash
git add backend/src/test/java/com/youthfit/admin/application/listener/PolicyReprocessRequestedEventListenerTest.java
git commit -m "$(cat <<'EOF'
test(be): listener 엣지 케이스 — policy 없음 / GUIDE 실패 격리 / ENRICHMENT reason

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2 — Stale 행 정리 Scheduler

### Task 4: PolicyProcessingStep 도메인 `markTimedOut` 메서드 + 단위 테스트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/PolicyProcessingStep.java`
- Create: `backend/src/test/java/com/youthfit/policy/domain/model/PolicyProcessingStepTest.java` (없으면 신규)

- [ ] **Step 1: 단위 테스트 작성**

`backend/src/test/java/com/youthfit/policy/domain/model/PolicyProcessingStepTest.java`:
```java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyProcessingStep 도메인")
class PolicyProcessingStepTest {

    @Test
    @DisplayName("markTimedOut() 호출 시 status=FAILED, reason=\"timeout\", finishedAt 설정")
    void markTimedOut_setsFailedAndTimeoutReason() {
        // given
        PolicyProcessingStep step = PolicyProcessingStep.start(10L, ProcessingStep.GUIDE, 1);
        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.IN_PROGRESS);

        // when
        step.markTimedOut();

        // then
        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(step.getReason()).isEqualTo("timeout");
        assertThat(step.getFinishedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd backend && ./gradlew test --tests "PolicyProcessingStepTest" -q`
Expected: FAIL — `markTimedOut()` 메서드 미존재로 컴파일 실패.

- [ ] **Step 3: 도메인 메서드 추가**

`PolicyProcessingStep.java` 의 기존 `finish` 메서드 바로 아래에 추가:

```java
    /**
     * 타임아웃으로 강제 마감. 운영 환경에서 listener 가 markFinished 호출에 실패한 (NPE/OOM/kill) 경우
     * {@code PolicyProcessingStepTimeoutScheduler} 가 호출.
     */
    public void markTimedOut() {
        finish(ProcessingStatus.FAILED, "timeout", null);
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "PolicyProcessingStepTest" -q`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/PolicyProcessingStep.java \
        backend/src/test/java/com/youthfit/policy/domain/model/PolicyProcessingStepTest.java
git commit -m "$(cat <<'EOF'
feat(be): PolicyProcessingStep.markTimedOut() 도메인 메서드 추가

스케줄러가 stale IN_PROGRESS 행을 FAILED("timeout") 로 마감하는 단일 진입점.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Repository `findActiveStaleBefore` 메서드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyProcessingStepRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepJpaRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepRepositoryImpl.java`

- [ ] **Step 1: 도메인 인터페이스에 메서드 시그니처 추가**

`PolicyProcessingStepRepository.java` 의 마지막 메서드 (`findLatestRowsByPolicyId`) 아래에 추가:

```java
    /**
     * IN_PROGRESS 이면서 startedAt 이 threshold 보다 이전인 stale 행 조회.
     * {@code PolicyProcessingStepTimeoutScheduler} 가 호출.
     */
    List<PolicyProcessingStep> findActiveStaleBefore(Instant threshold);
```

import 도 추가: `import java.time.Instant;`

- [ ] **Step 2: JPA 인터페이스에 derived query 추가**

`PolicyProcessingStepJpaRepository.java` 의 기존 메서드 아래에 추가:

```java
    List<PolicyProcessingStep> findByStatusAndStartedAtBefore(ProcessingStatus status, Instant threshold);
```

import 도 추가: `import com.youthfit.policy.domain.model.ProcessingStatus; import java.time.Instant;`

- [ ] **Step 3: Impl 에 위임 메서드 추가**

`PolicyProcessingStepRepositoryImpl.java` 의 마지막 메서드 아래에 추가:

```java
    @Override
    public List<PolicyProcessingStep> findActiveStaleBefore(Instant threshold) {
        return jpaRepository.findByStatusAndStartedAtBefore(ProcessingStatus.IN_PROGRESS, threshold);
    }
```

import 추가: `import java.time.Instant;`

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 기존 테스트 회귀 확인**

Run: `cd backend && ./gradlew test --tests "*PolicyProcessingStep*" -q`
Expected: PASS — Task 4 의 도메인 테스트 + 기존 통과 테스트.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicyProcessingStepRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepJpaRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepRepositoryImpl.java
git commit -m "$(cat <<'EOF'
feat(be): PolicyProcessingStepRepository.findActiveStaleBefore 추가

Spring Data derived query (findByStatusAndStartedAtBefore) 위임.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: PolicyProcessingStepTimeoutScheduler 신규 + 단위 테스트

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/infrastructure/scheduler/PolicyProcessingStepTimeoutScheduler.java`
- Create: `backend/src/test/java/com/youthfit/policy/infrastructure/scheduler/PolicyProcessingStepTimeoutSchedulerTest.java`

- [ ] **Step 1: Scheduler 단위 테스트 작성**

```java
package com.youthfit.policy.infrastructure.scheduler;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("PolicyProcessingStepTimeoutScheduler")
@ExtendWith(MockitoExtension.class)
class PolicyProcessingStepTimeoutSchedulerTest {

    @Mock private PolicyProcessingStepRepository repository;

    @Test
    @DisplayName("stale 행이 있으면 markTimedOut 호출 후 save")
    void expireStaleSteps_marksAndSavesEachStale() {
        // given — fixed clock: 2026-05-29T10:00:00Z, TIMEOUT 10분 → threshold = 09:50:00Z
        Instant now = Instant.parse("2026-05-29T10:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));
        PolicyProcessingStepTimeoutScheduler scheduler =
                new PolicyProcessingStepTimeoutScheduler(repository, fixedClock);

        PolicyProcessingStep stale1 = PolicyProcessingStep.start(1L, ProcessingStep.GUIDE, 1);
        PolicyProcessingStep stale2 = PolicyProcessingStep.start(2L, ProcessingStep.RAG_INDEXING, 1);
        Instant expectedThreshold = now.minusSeconds(600);
        given(repository.findActiveStaleBefore(expectedThreshold)).willReturn(List.of(stale1, stale2));

        // when
        scheduler.expireStaleSteps();

        // then
        ArgumentCaptor<PolicyProcessingStep> saved = ArgumentCaptor.forClass(PolicyProcessingStep.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .allSatisfy(s -> {
                    assertThat(s.getStatus().name()).isEqualTo("FAILED");
                    assertThat(s.getReason()).isEqualTo("timeout");
                    assertThat(s.getFinishedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("stale 행이 없으면 save 호출 없음")
    void expireStaleSteps_emptyList_noop() {
        // given
        Instant now = Instant.parse("2026-05-29T10:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));
        PolicyProcessingStepTimeoutScheduler scheduler =
                new PolicyProcessingStepTimeoutScheduler(repository, fixedClock);

        Instant expectedThreshold = now.minusSeconds(600);
        given(repository.findActiveStaleBefore(expectedThreshold)).willReturn(List.of());

        // when
        scheduler.expireStaleSteps();

        // then — findActiveStaleBefore 외 다른 호출 없음
        verify(repository).findActiveStaleBefore(expectedThreshold);
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd backend && ./gradlew test --tests "PolicyProcessingStepTimeoutSchedulerTest" -q`
Expected: FAIL — `PolicyProcessingStepTimeoutScheduler` 클래스 미존재로 컴파일 실패.

- [ ] **Step 3: Scheduler 신규 파일 작성**

```java
package com.youthfit.policy.infrastructure.scheduler;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * IN_PROGRESS 로 stale 한 {@code policy_processing_step} 행을 FAILED 로 강제 마감하는 스케줄러.
 *
 * <p>운영 환경 마이그레이션 + 상시 안전장치 두 가지 역할.
 * {@link com.youthfit.admin.application.listener.PolicyReprocessRequestedEventListener} 가
 * NPE / OOM / kill -9 등으로 {@code markFinished} 호출에 실패해도, 본 스케줄러가 1분 주기로
 * threshold (10분) 초과 행을 자동으로 FAILED 로 정리한다.</p>
 *
 * <p>{@link com.youthfit.policy.infrastructure.scheduler.EnrichmentJobTimeoutScheduler} 와 동일 패턴.</p>
 */
@Component
public class PolicyProcessingStepTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolicyProcessingStepTimeoutScheduler.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final PolicyProcessingStepRepository repository;
    private final Clock clock;

    public PolicyProcessingStepTimeoutScheduler(PolicyProcessingStepRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${policy.processing-step.timeout.fixed-delay-ms:60000}")
    @Transactional
    public void expireStaleSteps() {
        Instant threshold = Instant.now(clock).minus(TIMEOUT);
        List<PolicyProcessingStep> stale = repository.findActiveStaleBefore(threshold);
        if (stale.isEmpty()) return;

        for (PolicyProcessingStep step : stale) {
            step.markTimedOut();
            repository.save(step);
            log.warn("PolicyProcessingStep expired: id={} policyId={} step={} attempt={}",
                    step.getId(), step.getPolicyId(), step.getStep(), step.getAttempt());
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "PolicyProcessingStepTimeoutSchedulerTest" -q`
Expected: PASS — 2건 통과.

- [ ] **Step 5: 전체 백엔드 테스트 회귀 확인**

Run: `cd backend && ./gradlew test -q`
Expected: BUILD SUCCESSFUL — 기존 테스트 전부 통과.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/infrastructure/scheduler/PolicyProcessingStepTimeoutScheduler.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/scheduler/PolicyProcessingStepTimeoutSchedulerTest.java
git commit -m "$(cat <<'EOF'
feat(be): PolicyProcessingStepTimeoutScheduler 추가 (1분/10분)

IN_PROGRESS 로 stale 한 step 행을 FAILED("timeout") 로 자동 정리.
EnrichmentJobTimeoutScheduler 패턴. Clock 주입으로 테스트 결정성 확보.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3 — 통합 테스트

### Task 7: AdminPolicyProcessingService.reprocess → event → listener end-to-end

**Files:**
- Create: `backend/src/test/java/com/youthfit/admin/integration/PolicyReprocessIntegrationTest.java`

- [ ] **Step 1: 통합 테스트 작성**

목적: `AdminPolicyProcessingService.reprocess` 호출 시 (1) 4개 step 행이 생성되고 (2) listener 가 호출되어 (3) 4개 step 행 모두 IN_PROGRESS 가 아닌 SUCCESS/SKIPPED 로 마감되는지 검증. 외부 service 들 (Guide/Rule/RAG) 은 `@MockitoBean` 으로 mock.

```java
package com.youthfit.admin.integration;

import com.youthfit.admin.application.dto.ReprocessResult;
import com.youthfit.admin.application.service.AdminPolicyProcessingService;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.service.RagIndexingService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("PolicyReprocess 통합")
class PolicyReprocessIntegrationTest {

    @Autowired private AdminPolicyProcessingService adminService;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyProcessingStepRepository stepRepository;

    @MockitoBean private GuideGenerationService guideGenerationService;
    @MockitoBean private EligibilityRuleGenerationService eligibilityRuleGenerationService;
    @MockitoBean private RagIndexingService ragIndexingService;

    @Test
    @DisplayName("reprocess 호출 시 listener 가 4단계 step 행을 IN_PROGRESS 가 아닌 상태로 마감한다")
    void reprocess_triggersListenerAndFinishesAllSteps() {
        // given — 정책 저장 (Policy.builder() 패턴은 `GuideGenerationServiceHashTest.somePolicy()` 참고)
        Policy policy = Policy.builder()
                .title("통합 테스트 정책")
                .body("본문")
                .referenceYear(2026)
                .build();
        Policy saved = policyRepository.save(policy);

        // when
        ReprocessResult result = adminService.reprocess(saved.getId(), "통합 테스트");

        // then — 동기 시점에서 stepIds 4개가 IN_PROGRESS 상태로 생성됨
        assertThat(result.queued()).isTrue();
        assertThat(result.stepIds()).hasSize(4);

        // listener 가 @Async 라 대기 (CallerRunsPolicy 일 수도 있어 즉시 완료될 수도 있음)
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    Map<ProcessingStep, ProcessingStatus> latest = stepRepository
                            .findLatestStatusMapByPolicyIds(List.of(saved.getId()))
                            .getOrDefault(saved.getId(), Map.of());
                    // ENRICHMENT 는 SKIPPED, 나머지는 SUCCESS (mock 이 예외 안 던짐)
                    assertThat(latest.get(ProcessingStep.ENRICHMENT)).isEqualTo(ProcessingStatus.SKIPPED);
                    assertThat(latest.get(ProcessingStep.GUIDE)).isEqualTo(ProcessingStatus.SUCCESS);
                    assertThat(latest.get(ProcessingStep.RULE)).isEqualTo(ProcessingStatus.SUCCESS);
                    assertThat(latest.get(ProcessingStep.RAG_INDEXING)).isEqualTo(ProcessingStatus.SUCCESS);
                });

        // 각 step 행의 finished_at 이 null 아님 (스펙 §7 기준 2번)
        List<PolicyProcessingStep> rows = stepRepository.findLatestRowsByPolicyId(saved.getId());
        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(r -> assertThat(r.getFinishedAt()).isNotNull());
    }
}
```

> Note: `Policy.create(...)` 자리에 프로젝트의 실제 Policy 빌더 / factory 호출을 채워 넣는다. Task 1 의 listener test 가 mock 으로 처리한 것과 달리, 통합 테스트는 진짜 정책을 저장해야 한다. 기존 통합 테스트 (예: `AdminPolicyProcessingServiceIntegrationTest` 가 있다면 그 helper) 또는 Policy 의 정적 factory 시그니처를 참조해 채울 것. Awaitility 의존성이 build.gradle 에 없으면 추가 필요 (test 스코프).

- [ ] **Step 2: build.gradle Awaitility 의존성 확인 / 추가**

Run: `rtk grep "awaitility" /Users/taetaetae/IdeaProjects/youthfit/backend/build.gradle`

만약 없으면 `build.gradle` 의 testImplementation 블록에 추가:
```
testImplementation 'org.awaitility:awaitility:4.2.0'
```

(이미 있으면 Step 2 skip.)

- [ ] **Step 3: 통합 테스트 실행 → 통과 확인**

Run: `cd backend && ./gradlew test --tests "PolicyReprocessIntegrationTest" -q`
Expected: PASS — 5초 안에 4 step 행 마감 확인.

> 실패 시 디버깅:
> - `@Async` 가 동작 안 함 → `AsyncConfig.@EnableAsync` 가 test context 에 포함되는지 확인.
> - `Policy.create(...)` 빌더 시그니처 mismatch → 컴파일 에러 메시지에서 정확한 시그니처 확인 후 fix.
> - 테스트 격리 문제 (다른 정책 행과 충돌) → `@Transactional` 추가 또는 `@DirtiesContext`.

- [ ] **Step 4: 전체 백엔드 테스트 회귀 확인**

Run: `cd backend && ./gradlew test -q`
Expected: BUILD SUCCESSFUL — 기존 + 신규 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/test/java/com/youthfit/admin/integration/PolicyReprocessIntegrationTest.java
# Awaitility 추가했다면 build.gradle 도 함께
git add backend/build.gradle 2>/dev/null || true
git commit -m "$(cat <<'EOF'
test(be): policy reprocess listener 통합 테스트

@SpringBootTest 로 admin.reprocess → event → listener → step 마감 검증.
Awaitility 로 @Async 결과 5초 polling.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4 — spec done 처리 + PR 준비

### Task 8: spec 문서 DONE 표기 + 운영 검증 노트

**Files:**
- Modify: `docs/superpowers/specs/2026-05-29-policy-reprocess-listener-design.md`
- Rename: `docs/superpowers/specs/2026-05-29-policy-reprocess-listener-design.md` → `DONE_2026-05-29-policy-reprocess-listener-design.md`

- [ ] **Step 1: spec 의 상태 라인을 done 으로 변경**

`2026-05-29-policy-reprocess-listener-design.md` 의 첫 메타데이터 블록:
```markdown
> **상태**: spec (2026-05-29 작성, 같은 날 브레인스토밍으로 결정 확정)
```
→ 다음으로 교체:
```markdown
> **상태**: DONE (YYYY-MM-DD 머지 — 실제 머지일로 교체)
```

- [ ] **Step 2: 파일명 DONE_ prefix 추가**

```bash
git mv docs/superpowers/specs/2026-05-29-policy-reprocess-listener-design.md \
       docs/superpowers/specs/DONE_2026-05-29-policy-reprocess-listener-design.md
```

- [ ] **Step 3: 커밋**

```bash
git add docs/superpowers/specs/DONE_2026-05-29-policy-reprocess-listener-design.md
git commit -m "$(cat <<'EOF'
docs(spec): mark policy-reprocess-listener-design as done

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 4: PR 생성**

Run `create-pr` 스킬 또는:
```bash
git push -u origin <branch>
gh pr create --title "feat(be): policy reprocess listener + stale step timeout scheduler" --body "..."
```

PR body 에 포함:
- spec 링크 (`docs/superpowers/specs/DONE_2026-05-29-policy-reprocess-listener-design.md`)
- spec §4.3 의 "단계 추가 시 admin service.reprocess + listener 를 같은 PR 에서 수정" 계약 명시
- 운영 환경에서 stale 행 자동 정리가 시작되는 시점 안내 (배포 후 최대 1분)

---

## Verification Checklist

- [ ] PR 머지 후 운영 환경에서 정책 1건 "전체 재처리" 수동 트리거
- [ ] 어드민 대시보드에서 5초 안에 4단계 dot 가 SUCCESS/SKIPPED 로 변하는지 확인
- [ ] ENRICHMENT step.reason 이 "MVP: ENRICHMENT manual trigger 미연결" 인지 DB 에서 확인
- [ ] 기존 stale IN_PROGRESS 행이 있었다면 배포 후 1분 ~ 10분 안에 status=FAILED, reason=timeout 으로 정리되는지 확인
- [ ] `policy_document` 테이블에 새 chunk 가 들어왔는지 (RAG 실제 호출 검증)
- [ ] LLM 비용 대시보드에서 재처리 1건의 token 소비량이 평소 단일 단계와 동일한 수준인지 (CostGuard 가 GUIDE/RULE/RAG service 내부에 이미 적용되어 listener 가 별도 가드 둘 필요 없는지 확인)
