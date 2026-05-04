# 정책 ingest LLM 후속 처리 비동기 이벤트화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `IngestionService` / `AttachmentReindexService` 가 `GuideGenerationService` 와 `EligibilityRuleGenerationService` 를 직접 동기 호출하던 흐름을 Spring `ApplicationEvent` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("llmExecutor")` 기반 비동기 흐름으로 분리해 ingest API 응답 시간(현재 10~60s)을 1s 미만으로 줄이고, ingest 모듈에서 guide/eligibility 모듈에 대한 직접 의존을 제거한다.

**Architecture:** `common/event/` 에 도메인-순수 이벤트 record 두 개(`PolicyUpsertedEvent`, `PolicyAttachmentReindexedEvent`) 를 정의한다. `common/config/AsyncConfig` 에 전용 풀 `llmExecutor` (core=2, max=4, queueCapacity=100, CallerRunsPolicy, graceful shutdown 60s) 를 등록한다. `guide/application/listener/` 와 `eligibility/application/listener/` 에 각자 자기 모듈 서비스를 호출하는 리스너를 두어, `@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=true)` + `@Async("llmExecutor")` 조합으로 ingest 트랜잭션 commit 직후 별도 스레드에서 LLM 호출을 진행한다. 발행 조건은 spec 의 추천대로 `IngestionService.receivePolicy` 는 항상 발행, `AttachmentReindexService.reindex` 는 `result.updated()` 일 때만 발행으로 통일한다.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Spring Context (ApplicationEventPublisher, `@TransactionalEventListener`), Spring TaskExecutor (`@EnableAsync`, `ThreadPoolTaskExecutor`), JUnit 5, Mockito, AssertJ, Lombok.

**Spec:** `docs/superpowers/specs/DONE_2026-05-04-llm-async-events-design.md`

---

## File Structure

### 신규 파일 (5개)

| 파일 | 책임 |
|---|---|
| `backend/src/main/java/com/youthfit/common/event/PolicyUpsertedEvent.java` | record (Long policyId, String title) — 정책 ingest 트랜잭션 commit 후 발행 |
| `backend/src/main/java/com/youthfit/common/event/PolicyAttachmentReindexedEvent.java` | record (Long policyId) — 첨부 재인덱싱으로 본문이 갱신되었을 때만 발행 |
| `backend/src/main/java/com/youthfit/common/config/AsyncConfig.java` | `@EnableAsync` + `llmExecutor` Bean (core=2, max=4, queueCapacity=100, CallerRunsPolicy, graceful 60s) |
| `backend/src/main/java/com/youthfit/guide/application/listener/GuideGenerationEventListener.java` | 두 이벤트 모두 구독, `GuideGenerationService.generateGuide` 호출, 예외 catch+WARN 로그 |
| `backend/src/main/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListener.java` | 두 이벤트 모두 구독, `EligibilityRuleGenerationService.generateRules` 호출, 예외 catch+WARN 로그 |

### 신규 테스트 (2개)

| 파일 | 책임 |
|---|---|
| `backend/src/test/java/com/youthfit/guide/application/listener/GuideGenerationEventListenerTest.java` | 두 이벤트 핸들러가 service 호출 + 예외 swallow 동작 검증 |
| `backend/src/test/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListenerTest.java` | 동일 패턴 |

### 수정 파일 (4개)

| 파일 | 변경 |
|---|---|
| `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java` | 의존 제거(`GuideGenerationService`, `EligibilityRuleGenerationService`) → `ApplicationEventPublisher` 주입. `triggerGuideGeneration`, `triggerRuleGeneration` 헬퍼 삭제. `receivePolicy(...)` 에서 `eventPublisher.publishEvent(new PolicyUpsertedEvent(policyId, title))` 호출 |
| `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java` | 의존 제거(`GuideGenerationService`, `EligibilityRuleGenerationService`) → `ApplicationEventPublisher` 주입. `triggerRuleGeneration` 헬퍼 삭제. `result.updated()` 분기 안에서 `eventPublisher.publishEvent(new PolicyAttachmentReindexedEvent(resolvedId))` 호출 |
| `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java` | `@Mock GuideGenerationService` 제거 → `@Mock ApplicationEventPublisher`, 가이드 호출 검증 → publish 검증 |
| `backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceTest.java` | 동일 패턴 |

### 변경 없음

- `GuideGenerationService` / `EligibilityRuleGenerationService` 자체 코드는 변경하지 않는다 (외부에서 호출 위치만 바뀜).
- `AttachmentDownloadService` 와 `AttachmentAsyncConfig` 의 `@Async` 패턴은 그대로 유지 (spec out-of-scope).
- `POST /api/internal/guides/generate` 운영자 수동 호출도 동기 유지 (spec 결정 사항).

---

## Task 1: 이벤트 record 2개 추가

**목적:** 후속 task 가 의존할 도메인-순수 이벤트 record 를 먼저 정의한다. Spring/JPA/Lombok 의존이 없는 단순 record.

**Files:**
- Create: `backend/src/main/java/com/youthfit/common/event/PolicyUpsertedEvent.java`
- Create: `backend/src/main/java/com/youthfit/common/event/PolicyAttachmentReindexedEvent.java`

- [ ] **Step 1: `PolicyUpsertedEvent` 작성**

새 파일 `backend/src/main/java/com/youthfit/common/event/PolicyUpsertedEvent.java` 를 다음 내용으로 작성한다:

```java
package com.youthfit.common.event;

/**
 * 정책 ingest(신규/갱신) 트랜잭션이 정상 commit 된 직후 발행되는 이벤트.
 *
 * 구독자: GuideGenerationEventListener, EligibilityRuleGenerationEventListener
 *
 * 발행 위치: IngestionService.receivePolicy(...)
 */
public record PolicyUpsertedEvent(Long policyId, String title) {
}
```

- [ ] **Step 2: `PolicyAttachmentReindexedEvent` 작성**

새 파일 `backend/src/main/java/com/youthfit/common/event/PolicyAttachmentReindexedEvent.java` 를 다음 내용으로 작성한다:

```java
package com.youthfit.common.event;

/**
 * 첨부 재인덱싱(AttachmentReindexService.reindex) 결과로 정책 문서가 실제 갱신되었을 때만
 * 발행되는 이벤트. result.updated() == false 인 경우에는 발행되지 않는다.
 *
 * 구독자: GuideGenerationEventListener, EligibilityRuleGenerationEventListener
 *
 * 발행 위치: AttachmentReindexService.reindex(...)
 */
public record PolicyAttachmentReindexedEvent(Long policyId) {
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL. 새 record 두 개가 컴파일되어 다음 task 에서 import 가능 상태.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/common/event/PolicyUpsertedEvent.java \
        backend/src/main/java/com/youthfit/common/event/PolicyAttachmentReindexedEvent.java
git commit -m "feat(common): 정책 ingest/첨부 재인덱싱 이벤트 record 추가"
```

---

## Task 2: `AsyncConfig` 추가

**목적:** 가이드/룰 리스너가 사용할 전용 스레드 풀(`llmExecutor`) 을 등록한다. `@EnableAsync` 는 기존 `AttachmentAsyncConfig` 에 이미 있지만, 새 config 에서도 명시해 두어 attachment async 가 분리·이동되어도 안전하게 동작하도록 한다 (Spring 은 `@EnableAsync` 중복을 허용하며 단일 활성화로 처리한다).

**Files:**
- Create: `backend/src/main/java/com/youthfit/common/config/AsyncConfig.java`

- [ ] **Step 1: `AsyncConfig` 작성 (최종 코드)**

새 파일 `backend/src/main/java/com/youthfit/common/config/AsyncConfig.java` 를 다음 내용으로 작성한다:

```java
package com.youthfit.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 가이드/적합도 룰 LLM 후속 처리용 비동기 실행기.
 *
 * - 풀 크기: core 2 / max 4
 * - 큐 깊이: 100
 * - 거절 정책: CallerRunsPolicy (큐가 차면 publisher 스레드가 직접 실행하여 자연 throttle)
 * - 셧다운: 진행 중 작업 60초 대기 후 강제 종료
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "llmExecutor")
    public ThreadPoolTaskExecutor llmExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("llm-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(60);
        exec.initialize();
        return exec;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/common/config/AsyncConfig.java
git commit -m "feat(common): LLM 비동기 처리용 llmExecutor 빈 추가"
```

---

## Task 3: `GuideGenerationEventListener` 추가 (TDD)

**목적:** `PolicyUpsertedEvent` / `PolicyAttachmentReindexedEvent` 를 구독해 `GuideGenerationService.generateGuide` 를 호출하는 리스너를 작성한다. 단위 테스트는 어노테이션(`@Async`, `@TransactionalEventListener`) 자체를 검증하지 않고, 메서드를 직접 호출했을 때 의도대로 동작하는지를 확인한다 (어노테이션 동작은 Spring 표준이라 별도 통합 테스트 인프라 도입 시점까지는 신뢰).

**Files:**
- Create: `backend/src/test/java/com/youthfit/guide/application/listener/GuideGenerationEventListenerTest.java`
- Create: `backend/src/main/java/com/youthfit/guide/application/listener/GuideGenerationEventListener.java`

- [ ] **Step 1: 실패 테스트 작성**

새 파일 `backend/src/test/java/com/youthfit/guide/application/listener/GuideGenerationEventListenerTest.java` 를 다음 내용으로 작성한다:

```java
package com.youthfit.guide.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DisplayName("GuideGenerationEventListener")
@ExtendWith(MockitoExtension.class)
class GuideGenerationEventListenerTest {

    @Mock
    private GuideGenerationService guideGenerationService;

    @InjectMocks
    private GuideGenerationEventListener listener;

    @Test
    @DisplayName("PolicyUpsertedEvent 수신 시 policyId/title 로 가이드 생성을 호출한다")
    void onPolicyUpserted_callsGuideGeneration() {
        // given
        PolicyUpsertedEvent event = new PolicyUpsertedEvent(42L, "청년월세 지원");

        // when
        listener.onPolicyUpserted(event);

        // then
        ArgumentCaptor<GenerateGuideCommand> captor = ArgumentCaptor.forClass(GenerateGuideCommand.class);
        then(guideGenerationService).should().generateGuide(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(42L);
        assertThat(captor.getValue().policyTitle()).isEqualTo("청년월세 지원");
    }

    @Test
    @DisplayName("PolicyAttachmentReindexedEvent 수신 시 policyId 만으로 가이드 재생성을 호출한다 (title null)")
    void onAttachmentReindexed_callsGuideGenerationWithoutTitle() {
        // given
        PolicyAttachmentReindexedEvent event = new PolicyAttachmentReindexedEvent(7L);

        // when
        listener.onAttachmentReindexed(event);

        // then
        ArgumentCaptor<GenerateGuideCommand> captor = ArgumentCaptor.forClass(GenerateGuideCommand.class);
        then(guideGenerationService).should().generateGuide(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(7L);
        assertThat(captor.getValue().policyTitle()).isNull();
    }

    @Test
    @DisplayName("가이드 생성에서 예외가 던져져도 리스너는 예외를 전파하지 않는다 (PolicyUpsertedEvent)")
    void onPolicyUpserted_swallowsException() {
        // given
        given(guideGenerationService.generateGuide(any()))
                .willThrow(new RuntimeException("LLM 장애"));

        // when & then
        assertThatCode(() -> listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "t")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("가이드 재생성에서 예외가 던져져도 리스너는 예외를 전파하지 않는다 (PolicyAttachmentReindexedEvent)")
    void onAttachmentReindexed_swallowsException() {
        // given
        given(guideGenerationService.generateGuide(any()))
                .willThrow(new RuntimeException("LLM 장애"));

        // when & then
        assertThatCode(() -> listener.onAttachmentReindexed(new PolicyAttachmentReindexedEvent(1L)))
                .doesNotThrowAnyException();
    }
}
```

> **참고**: `GenerateGuideCommand` 의 필드 이름은 `policyId`, `policyTitle`, `documentContent` 이다 (`backend/src/main/java/com/youthfit/guide/application/dto/command/GenerateGuideCommand.java` 확인). 만약 record 필드가 다른 이름이라면 (예: `title`) 위 captor 검증과 다음 step 의 listener 코드 둘 다 그 이름으로 통일한다.

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.listener.GuideGenerationEventListenerTest"`
Expected: FAIL — `GuideGenerationEventListener` 클래스가 존재하지 않아 컴파일 실패.

- [ ] **Step 3: 리스너 구현 (최종 코드)**

새 파일 `backend/src/main/java/com/youthfit/guide/application/listener/GuideGenerationEventListener.java` 를 다음 내용으로 작성한다:

```java
package com.youthfit.guide.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GuideGenerationEventListener {

    private static final Logger log = LoggerFactory.getLogger(GuideGenerationEventListener.class);

    private final GuideGenerationService guideGenerationService;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyUpserted(PolicyUpsertedEvent event) {
        try {
            guideGenerationService.generateGuide(
                    new GenerateGuideCommand(event.policyId(), event.title(), null));
        } catch (Exception e) {
            log.warn("가이드 생성 실패 (event=PolicyUpsertedEvent): policyId={}", event.policyId(), e);
        }
    }

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAttachmentReindexed(PolicyAttachmentReindexedEvent event) {
        try {
            guideGenerationService.generateGuide(
                    new GenerateGuideCommand(event.policyId(), null, null));
        } catch (Exception e) {
            log.warn("가이드 재생성 실패 (event=PolicyAttachmentReindexedEvent): policyId={}", event.policyId(), e);
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.listener.GuideGenerationEventListenerTest"`
Expected: PASS — 4 tests succeed.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/listener/GuideGenerationEventListener.java \
        backend/src/test/java/com/youthfit/guide/application/listener/GuideGenerationEventListenerTest.java
git commit -m "feat(guide): 정책 ingest/재인덱싱 이벤트 구독 리스너 추가"
```

---

## Task 4: `EligibilityRuleGenerationEventListener` 추가 (TDD)

**목적:** Task 3 과 동일한 패턴으로 적합도 룰 추출 리스너를 추가한다.

**Files:**
- Create: `backend/src/test/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListenerTest.java`
- Create: `backend/src/main/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListener.java`

- [ ] **Step 1: 실패 테스트 작성**

새 파일 `backend/src/test/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListenerTest.java` 를 다음 내용으로 작성한다:

```java
package com.youthfit.eligibility.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DisplayName("EligibilityRuleGenerationEventListener")
@ExtendWith(MockitoExtension.class)
class EligibilityRuleGenerationEventListenerTest {

    @Mock
    private EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @InjectMocks
    private EligibilityRuleGenerationEventListener listener;

    @Test
    @DisplayName("PolicyUpsertedEvent 수신 시 policyId 로 룰 추출을 호출한다")
    void onPolicyUpserted_callsRuleGeneration() {
        // given
        PolicyUpsertedEvent event = new PolicyUpsertedEvent(42L, "청년월세 지원");

        // when
        listener.onPolicyUpserted(event);

        // then
        ArgumentCaptor<GenerateEligibilityRulesCommand> captor =
                ArgumentCaptor.forClass(GenerateEligibilityRulesCommand.class);
        then(eligibilityRuleGenerationService).should().generateRules(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("PolicyAttachmentReindexedEvent 수신 시 policyId 로 룰 재추출을 호출한다")
    void onAttachmentReindexed_callsRuleGeneration() {
        // given
        PolicyAttachmentReindexedEvent event = new PolicyAttachmentReindexedEvent(7L);

        // when
        listener.onAttachmentReindexed(event);

        // then
        ArgumentCaptor<GenerateEligibilityRulesCommand> captor =
                ArgumentCaptor.forClass(GenerateEligibilityRulesCommand.class);
        then(eligibilityRuleGenerationService).should().generateRules(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("룰 추출에서 예외가 던져져도 리스너는 예외를 전파하지 않는다 (PolicyUpsertedEvent)")
    void onPolicyUpserted_swallowsException() {
        // given
        given(eligibilityRuleGenerationService.generateRules(any()))
                .willThrow(new RuntimeException("LLM 장애"));

        // when & then
        assertThatCode(() -> listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "t")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("룰 재추출에서 예외가 던져져도 리스너는 예외를 전파하지 않는다 (PolicyAttachmentReindexedEvent)")
    void onAttachmentReindexed_swallowsException() {
        // given
        given(eligibilityRuleGenerationService.generateRules(any()))
                .willThrow(new RuntimeException("LLM 장애"));

        // when & then
        assertThatCode(() -> listener.onAttachmentReindexed(new PolicyAttachmentReindexedEvent(1L)))
                .doesNotThrowAnyException();
    }
}
```

> **참고**: `GenerateEligibilityRulesCommand` 의 record 필드가 `policyId` 인 것은 `IngestionService.triggerRuleGeneration` 의 사용처(`new GenerateEligibilityRulesCommand(policyId)`)에서 확인됨. 만약 필드가 추가되어 있으면 captor 검증을 그에 맞춘다.

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.eligibility.application.listener.EligibilityRuleGenerationEventListenerTest"`
Expected: FAIL — 리스너 클래스가 존재하지 않아 컴파일 실패.

- [ ] **Step 3: 리스너 구현 (최종 코드)**

새 파일 `backend/src/main/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListener.java` 를 다음 내용으로 작성한다:

```java
package com.youthfit.eligibility.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
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

    private final EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyUpserted(PolicyUpsertedEvent event) {
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
        try {
            eligibilityRuleGenerationService.generateRules(
                    new GenerateEligibilityRulesCommand(event.policyId()));
        } catch (Exception e) {
            log.warn("적합도 룰 재추출 실패 (event=PolicyAttachmentReindexedEvent): policyId={}", event.policyId(), e);
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.eligibility.application.listener.EligibilityRuleGenerationEventListenerTest"`
Expected: PASS — 4 tests succeed.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListener.java \
        backend/src/test/java/com/youthfit/eligibility/application/listener/EligibilityRuleGenerationEventListenerTest.java
git commit -m "feat(eligibility): 정책 ingest/재인덱싱 이벤트 구독 리스너 추가"
```

---

## Task 5: `IngestionService` 이벤트 발행으로 전환

**목적:** `IngestionService` 가 더 이상 `GuideGenerationService` / `EligibilityRuleGenerationService` 를 직접 호출하지 않고 `PolicyUpsertedEvent` 만 발행하도록 변경한다. 기존 단위 테스트의 가이드 호출 검증은 publish 검증으로 교체한다.

**Files:**
- Modify: `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`

- [ ] **Step 1: 테스트 변경 (실패 상태로 만든다)**

`backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java` 를 다음과 같이 수정한다.

먼저 import 블록에서 가이드 관련 import 를 제거하고 `ApplicationEventPublisher` / `PolicyUpsertedEvent` import 를 추가한다:

```java
// 제거할 import
import com.youthfit.guide.application.service.GuideGenerationService;

// 추가할 import
import com.youthfit.common.event.PolicyUpsertedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

`@Mock` 필드 블록을 다음으로 교체한다 (기존 `GuideGenerationService` mock 제거 + `ApplicationEventPublisher` mock 추가):

```java
    @Mock
    private PolicyIngestionService policyIngestionService;

    @Mock
    private PolicyPeriodLlmProvider policyPeriodLlmProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AttachmentDownloadService attachmentDownloadService;

    @Spy
    private PolicyPeriodExtractor policyPeriodExtractor = new PolicyPeriodExtractor();

    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Spy
    private CostGuard costGuard = new CostGuard(new CostGuardProperties(""));
```

기존 두 테스트 (`정책_등록_후_가이드_생성을_호출한다`, `가이드_생성_실패해도_ingestion은_성공`) 를 다음 두 테스트로 교체한다:

```java
        @Test
        @DisplayName("정책 등록 후 PolicyUpsertedEvent 를 발행한다 (policyId, title 포함)")
        void 정책_등록_후_PolicyUpsertedEvent_를_발행한다() {
            // Given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "일자리");
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(new PolicyIngestionResult(42L, true));

            // When
            ingestionService.receivePolicy(command);

            // Then
            ArgumentCaptor<PolicyUpsertedEvent> captor = ArgumentCaptor.forClass(PolicyUpsertedEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().policyId()).isEqualTo(42L);
            assertThat(captor.getValue().title()).isEqualTo(command.title());
        }

        @Test
        @DisplayName("가이드/룰은 더 이상 직접 호출되지 않는다 (이벤트 발행만 일어난다)")
        void 가이드와_룰은_직접_호출되지_않는다() {
            // Given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "일자리");
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(new PolicyIngestionResult(42L, true));

            // When
            assertThatCode(() -> ingestionService.receivePolicy(command))
                    .doesNotThrowAnyException();

            // Then: eventPublisher 외엔 LLM 의존이 주입되지 않으므로, 단순히 publish 가 한 번 일어났는지로 검증
            then(eventPublisher).should().publishEvent(any(PolicyUpsertedEvent.class));
        }
```

기존 다른 테스트(카테고리/기간 등) 는 `GuideGenerationService` mock 에 의존하지 않으므로 그대로 둔다.

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.IngestionServiceTest"`
Expected: 컴파일 단계에서 실패 — `IngestionService` 의 생성자 시그니처가 아직 `ApplicationEventPublisher` 를 받지 않으므로 `@InjectMocks` 가 주입할 필드를 찾지 못한다. (정확히는 `eventPublisher` 필드가 IngestionService 에 없어 unused mock 경고/주입 실패.) 이 단계의 실패 메시지는 다음 step 에서 해소한다.

- [ ] **Step 3: `IngestionService` 변경**

`backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java` 를 다음과 같이 수정한다.

import 블록에서 다음 두 줄을 **제거**한다:

```java
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
```

import 블록에 다음 두 줄을 **추가**한다:

```java
import com.youthfit.common.event.PolicyUpsertedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

필드 선언부의 두 의존을 **삭제**하고 `eventPublisher` 를 추가한다:

```java
    private final PolicyIngestionService policyIngestionService;
    private final ObjectMapper objectMapper;
    private final PolicyPeriodExtractor policyPeriodExtractor;
    private final PolicyPeriodLlmProvider policyPeriodLlmProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final AttachmentDownloadService attachmentDownloadService;
    private final CostGuard costGuard;
```

`receivePolicy(...)` 메서드 끝부분에서 가이드/룰 직접 호출 두 줄을 **이벤트 발행** 한 줄로 교체한다 (변경 후 전체 모습):

```java
        PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
        eventPublisher.publishEvent(new PolicyUpsertedEvent(ingestionResult.policyId(), command.title()));
        triggerAttachmentDownload(ingestionResult.policyId());

        return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
```

`triggerGuideGeneration(...)` 와 `triggerRuleGeneration(...)` private 메서드 두 개를 **완전히 삭제**한다. (`triggerAttachmentDownload` 만 남는다.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.IngestionServiceTest"`
Expected: PASS — 모든 IngestionServiceTest 가 성공한다.

- [ ] **Step 5: 모듈 전체 빌드 검증**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL — 다른 모듈에서 `IngestionService` 의 시그니처를 import 하는 곳이 없는지 회귀 확인.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java
git commit -m "refactor(ingestion): IngestionService 가이드/룰 직접 호출을 PolicyUpsertedEvent 발행으로 전환"
```

---

## Task 6: `AttachmentReindexService` 이벤트 발행으로 전환

**목적:** `AttachmentReindexService.reindex(...)` 가 `result.updated()` 일 때만 `PolicyAttachmentReindexedEvent` 를 발행하도록 변경한다. 기존 단위 테스트도 publish 검증으로 교체한다.

**Files:**
- Modify: `backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceTest.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java`

- [ ] **Step 1: 테스트 변경 (실패 상태로 만든다)**

`backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceTest.java` 를 다음과 같이 수정한다.

import 블록에서 가이드 import 를 제거하고 이벤트/publisher import 를 추가한다:

```java
// 제거할 import
import com.youthfit.guide.application.service.GuideGenerationService;

// 추가할 import
import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

`@Mock` 필드 블록을 다음으로 교체한다:

```java
    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyAttachmentRepository attachmentRepository;
    @Mock private RagIndexingService ragIndexingService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Spy private CostGuard costGuard = new CostGuard(new CostGuardProperties(""));
    @InjectMocks private AttachmentReindexService sut;
```

기존 테스트 `reindex_정상_정책본문과_첨부텍스트를_합쳐_RagIndexing_호출` 마지막 줄 `verify(guideGenerationService).generateGuide(any());` 를 다음으로 교체한다:

```java
        ArgumentCaptor<PolicyAttachmentReindexedEvent> evt =
                ArgumentCaptor.forClass(PolicyAttachmentReindexedEvent.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertThat(evt.getValue().policyId()).isEqualTo(1L);
```

기존 테스트 `reindex_updated_false_면_가이드_재생성_안함` 의 검증을 다음으로 교체한다 (이벤트 발행도 일어나지 않아야 함). 테스트 이름도 의미에 맞춰 변경한다:

```java
    @Test
    void reindex_updated_false_면_PolicyAttachmentReindexedEvent_발행_안함() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(1L);
        when(policy.getBody()).thenReturn("body");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of());
        when(ragIndexingService.indexPolicyDocument(any())).thenReturn(new IndexingResult(1L, 0, false));

        sut.reindex(1L);

        verify(eventPublisher, never()).publishEvent(any(PolicyAttachmentReindexedEvent.class));
    }
```

`mergeContent_*` 테스트 두 개와 `reindex_200KB_초과_시_초과분_첨부_생략` 은 가이드 호출과 무관하므로 그대로 둔다 (`reindex_200KB_*` 는 이벤트 발행이 일어나도 verify 가 없어 통과한다).

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.AttachmentReindexServiceTest"`
Expected: 컴파일 또는 실행 단계에서 실패 — `AttachmentReindexService` 가 아직 `ApplicationEventPublisher` 를 받지 않는다.

- [ ] **Step 3: `AttachmentReindexService` 변경**

`backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java` 를 다음과 같이 수정한다.

import 블록에서 다음을 **제거**한다:

```java
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
```

import 블록에 다음을 **추가**한다:

```java
import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

필드 선언부에서 두 LLM 서비스를 **삭제**하고 `eventPublisher` 를 추가한다:

```java
    private final PolicyRepository policyRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final RagIndexingService ragIndexingService;
    private final ApplicationEventPublisher eventPublisher;
    private final CostGuard costGuard;
```

`reindex(Long policyId)` 메서드 마지막의 `if (result.updated()) { ... }` 블록을 다음으로 교체한다:

```java
        if (result.updated()) {
            eventPublisher.publishEvent(new PolicyAttachmentReindexedEvent(resolvedId));
            log.info("attachment reindex event published: policyId={}", resolvedId);
        }
```

`triggerRuleGeneration(Long policyId)` private 메서드를 **완전히 삭제**한다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.AttachmentReindexServiceTest"`
Expected: PASS — 모든 AttachmentReindexServiceTest 가 성공한다.

- [ ] **Step 5: 백엔드 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 회귀 없음.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceTest.java
git commit -m "refactor(ingestion): AttachmentReindexService 가이드/룰 직접 호출을 PolicyAttachmentReindexedEvent 발행으로 전환"
```

---

## Task 7: 회귀 검증 + 운영 메모

**목적:** 통합 테스트 인프라(`@SpringBootTest`) 도입은 spec out-of-scope 이므로, 핵심 회귀는 (a) 전체 단위 테스트 + (b) 수동 ingest 1건 응답 시간 측정으로 본다. spec 의 PR/머지 시점에 `DONE_` 접두사를 부여한다.

**Files:**
- 변경 없음 (검증 + 문서 작업)

- [ ] **Step 1: 전체 빌드 + 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL — 모든 모듈 컴파일 + 테스트 통과.

- [ ] **Step 2: 수동 ingest 응답 시간 측정 (로컬)**

다음 절차로 ingest API 응답 시간이 1초 미만으로 떨어지는지 확인한다:

1. `docker compose up -d` (postgres, redis, backend) — 백엔드 컨테이너 재기동 시 `--build backend` 필요
2. CostGuard allowlist 정책 1개 (예: 정책 ID 30 의 외부 식별자) 를 사용해 다음 명령으로 ingest 호출:

```bash
time curl -X POST http://localhost:8080/api/internal/ingestion/policy \
  -H "X-Internal-Api-Key: changeme" \
  -H "Content-Type: application/json" \
  -d @sample-ingest-payload.json
```

3. **기대치**: 응답 시간 (`time` 의 `real` 값) 이 1초 미만. 변경 전엔 10~60초 였음.
4. 수 분 뒤 정책에 대한 가이드/룰이 DB 에 저장되었는지 확인:
   - `SELECT id, policy_id, prompt_version FROM guides WHERE policy_id = <id>;`
   - `SELECT count(*) FROM eligibility_rules WHERE policy_id = <id>;`
5. 결과를 PR 본문에 측정값으로 첨부.

- [ ] **Step 3: 그레이스풀 셧다운 확인 (선택)**

`docker compose stop backend` 직후 백엔드 컨테이너 로그에서 `llm-` prefix 스레드가 진행 중이었다면 60초까지 대기 후 종료되는지 확인. 검증이 어려우면 코드/설정만 확인하고 패스 — `AsyncConfig` 의 `setWaitForTasksToCompleteOnShutdown(true)` + `setAwaitTerminationSeconds(60)` 두 줄이 있는지 grep:

```bash
grep -nE "WaitForTasksToCompleteOnShutdown|awaitTerminationSeconds" \
     backend/src/main/java/com/youthfit/common/config/AsyncConfig.java
```

Expected: 두 줄 매칭.

- [ ] **Step 4: PR 생성 / 머지 후 spec 파일에 `DONE_` 접두사 부여**

PR 머지 직후 다음을 수행한다:

```bash
git mv docs/superpowers/specs/2026-05-04-llm-async-events-design.md \
       docs/superpowers/specs/DONE_2026-05-04-llm-async-events-design.md
git mv docs/superpowers/plans/2026-05-04-llm-async-events.md \
       docs/superpowers/plans/DONE_2026-05-04-llm-async-events.md
```

본문에 다른 문서로의 참조 링크가 있다면 함께 갱신 (`grep -rn "2026-05-04-llm-async-events" docs/`).

```bash
git commit -m "docs: LLM 비동기 이벤트화 spec/plan 완료 표시 (DONE_ 접두사)"
```

---

## 후속/미결 항목

- **외부 큐 도입** (Kafka / Redis Streams / SQS): v0 범위 외. 인프로세스 한계(서버 죽으면 이벤트 누락)는 다음 ingest 사이클의 sourceHash 비교로 자연 복구되며, 이 한계가 운영상 문제로 드러날 때 본격 검토.
- **자동 재시도 / 데드레터**: 외부 큐 도입과 함께.
- **메트릭 대시보드** (큐 깊이 / 처리 시간 / 실패율): 운영 데이터 보고 결정. v0 에서는 로그 + `CallerRunsPolicy` 백프레셔로만 관측.
- **`@SpringBootTest` + testcontainers 통합 테스트 인프라**: 별도 큰 사이클로 진행. 본 작업의 슬라이스 + Mockito 검증으로 v0 출시 충분.
- **`AttachmentDownloadService` 의 기존 `@Async` 를 이벤트로 통합**: 우선순위 낮음. 동작 안정 시 그대로 둔다.
- **운영자 수동 호출 비동기화** (`POST /api/internal/guides/generate`): UX 상 동기 유지로 결정.

---

## PR 분할 제안

본 plan 은 Task 1~7 을 하나의 PR 로 묶어도 무방하다 (변경 라인이 작고 회귀 위험이 낮음). 다만 리뷰 시간이 길어질 것 같으면 다음 두 PR 로 분할 가능:

- **PR 1**: Task 1~4 (이벤트 record + AsyncConfig + 두 리스너). 현재 코드는 그대로라 리스너가 호출되지 않는 dead-path 상태이므로 무해하게 머지 가능.
- **PR 2**: Task 5~7 (IngestionService / AttachmentReindexService 전환 + 회귀 검증). 이때부터 실제 비동기 흐름 활성화.

분할 시 PR 2 머지 시점 직후가 응답 시간 단축이 관찰되는 시점이라는 것을 운영 노트에 명시한다.
