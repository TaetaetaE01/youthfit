# 정책 ingest LLM 후속 처리 비동기 이벤트화 — 설계

- 작성일: 2026-05-04
- 범위: 정책 ingest / 첨부 재인덱싱 시점에 자동 호출되는 LLM 작업(가이드 생성, 적합도 룰 추출)을 이벤트 기반 비동기로 분리
- 의존: `feat/eligibility-rule-extraction` 브랜치(가이드 + 적합도 룰 두 동기 호출이 ingest 응답 시간에 직렬로 누적)가 머지된 후 시작하는 후속 스펙
- v0 범위 외: 외부 큐(Kafka/Redis Streams/SQS), 자동 재시도/데드레터, 메트릭 대시보드, 도메인 이벤트 소싱

## 1. 배경

`IngestionService.ingestPolicy(...)`는 한 정책당 다음을 동기로 실행한다:

1. 정책 DB 저장
2. `triggerGuideGeneration` — `GuideGenerationService.generateGuide` (OpenAI Chat 1~2회 + 검증·재시도, 5~30초)
3. `triggerRuleGeneration` — `EligibilityRuleGenerationService.generateRules` (OpenAI Chat 1~2회, 5~30초)
4. `triggerAttachmentDownload` — `@Async`로 이미 비동기

`AttachmentReindexService.reindex(...)`도 같은 패턴 (가이드 + 룰 두 동기 호출, `result.updated()` 시).

문제:
- ingest API 응답 시간이 LLM 응답 시간을 그대로 흡수 (한 정책당 10~60초). n8n 등 호출자 타임아웃 위험.
- ingest 트랜잭션이 LLM round-trip 동안 DB 커넥션을 점유 (long-lived txn, 커넥션 풀 압박).
- ingest 처리량(throughput)이 LLM tail latency에 묶임.
- 운영자 수동 호출(`POST /api/internal/guides/generate`)은 동기여도 무방하지만 자동 트리거는 분리되어야 함.

해결: ingest는 **DB 저장 + 이벤트 발행만 동기**로 처리하고, 가이드/룰 LLM 호출은 **트랜잭션 commit 직후 별도 스레드에서 비동기**로 실행한다.

## 2. 결정 사항 요약 (가정 — 검토 후 수정 가능)

| 결정 | 선택 | 근거 |
|---|---|---|
| 비동기 패턴 | Spring `ApplicationEvent` + `@TransactionalEventListener(phase=AFTER_COMMIT)` + `@Async` | 모듈 결합도 ↓ (ingest가 guide/eligibility를 직접 호출 안 함). DB commit 후 실행되어 일관성 보장. v0 범위인 외부 큐 회피. |
| 큐 인프라 | 인메모리 Spring `ThreadPoolTaskExecutor` (이름 `llmExecutor`) | CLAUDE.md "v0 제외: 이벤트 드리븐 아키텍처"는 외부 큐(Kafka 등) 의미로 해석. 인프로세스 이벤트는 Spring 표준 패턴이라 v0 안에서 허용 |
| 이벤트 종류 | `PolicyUpsertedEvent(policyId)`, `PolicyAttachmentReindexedEvent(policyId)` | 두 트리거 의미 분리. 같은 리스너 셋이 두 이벤트 모두 구독 가능 |
| 리스너 위치 | `guide/application/listener/`, `eligibility/application/listener/` | 각 모듈이 자기 LLM 호출을 자기 모듈 안에서 트리거. `ingestion`이 `guide`/`eligibility`를 직접 의존하지 않게 됨 |
| 실패 정책 | 리스너 안의 LLM 실패는 ingest로 전파 안 함. 서비스 내부에서 catch + ERROR 로그 (현재 동작 유지) | 이벤트 패턴이라 그쪽 격리는 자연스러움 |
| 재시도 | v0에서는 별도 재시도 큐 없음. 다음 ingest 사이클에 sourceHash 비교로 자연 복구 | 외부 큐 없이 안전하게 가능한 한도 |
| 운영자 수동 호출 | `POST /api/internal/guides/generate` 동기 유지 | 운영자가 결과를 기다리는 UX. 비동기로 바꾸면 응답이 빈약해짐 |
| 그레이스풀 셧다운 | `@PreDestroy`로 `llmExecutor.shutdown()` + `awaitTermination(60s)` | 서버 재시작 시 진행 중 LLM 호출이 끊기지 않도록 |

## 3. 아키텍처

### 3.1 호출 흐름

```
[n8n] → IngestionController → IngestionService.ingestPolicy
  ├ DB 저장 (트랜잭션 내)
  ├ eventPublisher.publishEvent(new PolicyUpsertedEvent(policyId))
  ├ triggerAttachmentDownload(...)   ← 이미 비동기
  └ 트랜잭션 commit
  
[AFTER_COMMIT 훅, llmExecutor 별도 스레드]
  ├ GuideGenerationEventListener.onPolicyUpserted(event)
  │   └ guideGenerationService.generateGuide(...)        (자체 새 트랜잭션)
  └ EligibilityRuleGenerationEventListener.onPolicyUpserted(event)
      └ eligibilityRuleGenerationService.generateRules(...) (자체 새 트랜잭션)
```

핵심:
- `@TransactionalEventListener(phase=AFTER_COMMIT)`: ingest 트랜잭션이 정상 커밋된 후에만 리스너 실행. 롤백 시 LLM 호출 안 일어남.
- `@Async("llmExecutor")`: 같은 풀에서 두 리스너가 병렬로 실행됨 (혹은 풀을 분리해도 됨).
- 리스너는 자기 모듈 안의 자기 서비스를 호출 → ingest 모듈은 더 이상 guide/eligibility 모듈을 직접 의존하지 않음. 모듈 경계 더 깨끗해짐.

### 3.2 모듈 의존 변화

```
변경 전:
  ingestion/application → guide/application
  ingestion/application → eligibility/application

변경 후:
  ingestion/application → common/event    (이벤트 record만)
  guide/application → ingestion 이벤트 구독 (publish-subscribe)
  eligibility/application → ingestion 이벤트 구독 (publish-subscribe)
```

호출 의존이 사라지고 이벤트 구독으로 전환. ingest는 guide/eligibility의 존재를 모름.

## 4. 신규/변경 파일

| 레이어 | 파일 | 변경 |
|---|---|---|
| Common | `common/event/PolicyUpsertedEvent.java` | **신규** record(Long policyId, String title) |
| Common | `common/event/PolicyAttachmentReindexedEvent.java` | **신규** record(Long policyId) |
| Common | `common/config/AsyncConfig.java` | **신규** `@EnableAsync` + `llmExecutor` Bean (core=2, max=4, queueCapacity=100, CallerRunsPolicy) |
| Application | `ingestion/application/service/IngestionService.java` | guide/rule 직접 호출 제거 → `eventPublisher.publishEvent(new PolicyUpsertedEvent(policyId, title))`. 의존 필드 2개 제거 |
| Application | `ingestion/application/service/AttachmentReindexService.java` | guide/rule 직접 호출 제거 → `eventPublisher.publishEvent(new PolicyAttachmentReindexedEvent(policyId))`. `result.updated()` 시에만 발행. 의존 필드 2개 제거 |
| Application | `guide/application/listener/GuideGenerationEventListener.java` | **신규** `@Component`, 두 메서드 (`@TransactionalEventListener @Async("llmExecutor")` for both events) |
| Application | `eligibility/application/listener/EligibilityRuleGenerationEventListener.java` | **신규** 동일 패턴 |
| Test | 각 리스너 단위 테스트 (Mockito) | **신규** |
| Test | `IngestionServiceTest` (있으면 수정) | guide/rule mock 제거 → `ApplicationEventPublisher` mock으로 publish 검증 |

기존 `EligibilityRuleGenerationService` / `GuideGenerationService` 자체 코드는 변경 없음.

## 5. 흐름 비교

### 5.1 정책 ingest

**현재** (한 호출 = 한 트랜잭션 + 두 LLM 직렬):
```
IngestionService.ingestPolicy(...)
├ DB 저장
├ triggerGuideGeneration(...)        ← 동기, 5~30s
├ triggerRuleGeneration(...)         ← 동기, 5~30s
└ triggerAttachmentDownload(...)     ← 비동기
응답 시간: 10~60초
DB 커넥션 점유: 10~60초
```

**변경 후**:
```
IngestionService.ingestPolicy(...)
├ DB 저장
├ publishEvent(PolicyUpsertedEvent(policyId, title))   ← 즉시 반환
└ triggerAttachmentDownload(...)
응답 시간: 100ms~1s
DB 커넥션 점유: 100ms~1s

[비동기 별도 스레드]
GuideGenerationEventListener         ← 5~30s, 자체 트랜잭션
EligibilityRuleGenerationEventListener ← 5~30s, 자체 트랜잭션 (병렬)
```

### 5.2 첨부 재인덱싱

`AttachmentReindexService.reindex(...)`도 같은 패턴. 다만 `result.updated() == false`이면 **이벤트 발행 안 함** (불필요한 재추출 방지, 현재 동작과 동일).

### 5.3 운영자 수동 호출

`POST /api/internal/guides/generate`는 그대로 동기 유지. 운영자가 결과를 기다려 검수하는 UX이므로 변경하지 않음.

## 6. 핵심 코드 스케치

### 6.1 이벤트 record

```java
package com.youthfit.common.event;

public record PolicyUpsertedEvent(Long policyId, String title) {}
```

```java
package com.youthfit.common.event;

public record PolicyAttachmentReindexedEvent(Long policyId) {}
```

### 6.2 AsyncConfig

```java
package com.youthfit.common.config;

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

### 6.3 IngestionService 변경

```java
// 의존 제거: GuideGenerationService, EligibilityRuleGenerationService
// 의존 추가: ApplicationEventPublisher

private final ApplicationEventPublisher eventPublisher;

// ingestPolicy(...) 안의 호출부:
eventPublisher.publishEvent(new PolicyUpsertedEvent(policyId, command.title()));
// triggerGuideGeneration / triggerRuleGeneration 헬퍼 메서드 둘 다 삭제
```

### 6.4 GuideGenerationEventListener (신규)

```java
package com.youthfit.guide.application.listener;

@Component
@RequiredArgsConstructor
public class GuideGenerationEventListener {

    private static final Logger log = LoggerFactory.getLogger(GuideGenerationEventListener.class);
    private final GuideGenerationService guideGenerationService;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPolicyUpserted(PolicyUpsertedEvent event) {
        try {
            guideGenerationService.generateGuide(
                    new GenerateGuideCommand(event.policyId(), event.title(), null));
        } catch (Exception e) {
            log.warn("가이드 생성 실패 (event): policyId={}", event.policyId(), e);
        }
    }

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttachmentReindexed(PolicyAttachmentReindexedEvent event) {
        try {
            // policy 조회는 GuideGenerationService 내부에서 수행
            guideGenerationService.generateGuide(
                    new GenerateGuideCommand(event.policyId(), null, null));
        } catch (Exception e) {
            log.warn("가이드 재생성 실패 (event): policyId={}", event.policyId(), e);
        }
    }
}
```

(`EligibilityRuleGenerationEventListener`는 시그니처만 다르고 같은 패턴.)

## 7. 에러 처리

| 시나리오 | 처리 |
|---|---|
| 이벤트 발행 자체 실패 | 거의 발생 안 함 (`ApplicationEventPublisher.publishEvent`는 in-memory 호출). 발생하면 ingest 실패로 전파 (현재와 동일) |
| 리스너의 LLM 실패 | `GuideGenerationService` / `EligibilityRuleGenerationService`가 내부적으로 catch + 로깅. 추가로 리스너에서도 try-catch (방어) |
| 트랜잭션 commit 후 서버가 죽음 (이벤트 누락) | 인프로세스 한계. 같은 정책이 다음 ingest 사이클에 다시 들어오면 sourceHash 비교로 자연 복구. 같은 정책이 다시 안 들어오면 운영자가 `POST /api/internal/guides/generate` 또는 별도 재처리 API로 수동 트리거 |
| 두 리스너 중 한쪽만 실패 | 다른 쪽은 정상 진행. 다음 사이클 자연 복구 |
| TaskExecutor 큐 가득 | `CallerRunsPolicy`로 caller(이벤트 publisher 스레드)가 직접 실행 → 자연 throttle. 즉, ingest API 응답 시간이 일시적으로 늘 수 있지만 작업 유실은 없음 |
| TransactionPhase.AFTER_COMMIT인데 트랜잭션 없는 컨텍스트에서 발행 | 기본 동작은 silently 무시. `@TransactionalEventListener(fallbackExecution = true)` 옵션으로 트랜잭션 없을 때도 실행하도록 명시 (권장) |

## 8. 운영 / 관측

- **로그**: 각 리스너 시작/종료/실패 시 INFO/WARN/ERROR. policyId, eventType, 처리 시간 포함
- **TaskExecutor 큐 깊이**: v0에서는 별도 메트릭 안 둠. 큐가 차면 `CallerRunsPolicy` 백프레셔로 자연 throttle. 운영 데이터 보고 결정.
- **수동 재처리**: `POST /api/internal/guides/generate`로 가이드, 적합도 룰은 `IngestionController`의 정책 재처리 트리거 또는 `AttachmentReindexService` 호출로 자연 재실행
- **그레이스풀 셧다운**: `llmExecutor.setWaitForTasksToCompleteOnShutdown(true)` + `setAwaitTerminationSeconds(60)`. 서버 재시작 시 진행 중인 LLM 호출 60초까지 기다림

## 9. 테스트 전략

### 9.1 단위 테스트

- `GuideGenerationEventListenerTest` (Mockito): 이벤트 호출 시 service 호출 검증, 예외 시 swallow + 로그
- `EligibilityRuleGenerationEventListenerTest`: 동일
- `IngestionServiceTest` 수정: guide/rule mock 제거 → `ApplicationEventPublisher` mock 추가, `publishEvent` 호출 검증
- `AttachmentReindexServiceTest`: 동일

### 9.2 통합 테스트 (가능 범위)

현재 프로젝트는 `@SpringBootTest` 인프라가 미흡하지만 (`feat/eligibility-rule-extraction`의 `EligibilityControllerTest`가 슬라이스로 우회), 이번 작업은 Spring 컨텍스트가 필수라서 슬라이스만으로 검증이 부족.

옵션:
- (a) 슬라이스 + 모킹으로 진행 (검증 폭 좁음)
- (b) 작업과 함께 `@SpringBootTest` 인프라 도입 (testcontainers-postgres, 큐도 검증) — 별도 Out-of-scope 항목으로 분리 가능

추천: (a) v0에서는 슬라이스 + Mockito로 충분. 인프라는 다음 큰 사이클에 함께.

### 9.3 회귀 테스트

- ingest API 응답 시간 단축 확인 (수동, allowlist 정책 1개로 측정)
- 동일 sourceHash 정책 재ingest 시 LLM 호출 안 일어나는지 확인 (기존 동작 유지)

## 10. 마이그레이션 / 배포

1. 이벤트 record + AsyncConfig 도입 (DB 변경 없음).
2. 리스너 빈 등록.
3. `IngestionService` / `AttachmentReindexService`에서 직접 호출을 이벤트 발행으로 교체.
4. 배포 후 ingest 응답 시간 모니터링 (allowlist 정책 1개로 수동 ingest, 응답 시간이 1초 미만으로 떨어지는지 확인).
5. 가이드/룰 결과가 DB에 정상 저장되는지 별도 모니터링 (스토리지 직접 확인).
6. 롤백: 이벤트 발행 한 줄을 직접 호출로 되돌리는 것이 즉시 롤백 (리스너 코드는 무해, 이벤트가 발행 안 되면 자연스럽게 비활성화).

## 11. Out-of-scope

- 외부 큐 (Kafka, Redis Streams, SQS) — v0 범위 외 (CLAUDE.md 명시).
- 자동 재시도 / 데드레터 큐 — 인프로세스 이벤트로는 한계. 외부 큐 도입 시 재고.
- 메트릭 대시보드 (큐 깊이, 처리 시간, 실패율) — 운영 데이터 보고 결정.
- 운영자 수동 호출(`/api/internal/guides/generate`) 비동기화 — UX상 동기 유지.
- 이벤트 소싱 / 이벤트 스토어 — 단순 비동기 트리거 메커니즘이지 도메인 이벤트 패턴은 아님.
- 통합 테스트 인프라(`@SpringBootTest` + testcontainers) 도입 — 별도 작업.
- AttachmentDownloadService의 `@Async` 패턴을 이벤트로 통합 — 이미 동작 중이라 우선순위 낮음.
