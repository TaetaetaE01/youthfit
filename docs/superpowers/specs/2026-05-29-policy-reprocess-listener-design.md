# PolicyReprocessRequestedEvent Listener 구현 설계

> **상태**: spec (2026-05-29 작성, 같은 날 브레인스토밍으로 결정 확정)
> **선행**: [DONE_2026-05-29-admin-policy-processing-dashboard-design.md](./DONE_2026-05-29-admin-policy-processing-dashboard-design.md) — `PolicyReprocessRequestedEvent` 발행 측 구현 완료
> **모듈**: `admin` (listener 추가), `policy` (stale 정리 scheduler 추가), `guide` / `eligibility` / `rag` (기존 service 재사용)
> **관련 이슈**: PR #127 의 follow-up — Major 4번 (Reprocess listener)

## 1. 배경 / 동기

PR #127 (어드민 정책 처리 현황 대시보드) 가 머지되면서 운영자에게 "전체 재처리" 버튼이 노출되었다. 그러나 발행되는 `PolicyReprocessRequestedEvent` 의 listener 가 없어 다음 문제가 발생한다:

1. **버튼이 동작하지 않음** — `admin.application.service.AdminPolicyProcessingService.reprocess` 가 ENRICHMENT/GUIDE/RULE/RAG 4단계 `markStarted` 호출 후 이벤트만 발행. listener 가 없어 단계별 외부 호출 (Gemini, OpenAI, pgvector) 이 일어나지 않는다.
2. **step 행이 IN_PROGRESS 로 영구 유지** — `markFinished` 호출자가 없어 대시보드에서 "처리 중…" 상태가 계속 보인다. 운영자가 "버튼 또 눌러도 되나?" 헷갈린다.
3. **부분적 사용 불가** — 단일 단계 재실행 (retryStep) 은 동작하지만 "전체 재처리" 만 깨진 상태라 일관성 없는 UX.

해결: `PolicyReprocessRequestedEvent` 를 듣는 listener 를 추가해 4단계를 실제로 실행하고 step 행을 SUCCESS / FAILED / SKIPPED 로 마감한다.

## 2. 접근 비교

### 옵션 A — 각 모듈에 신규 listener 추가 (4 files)

guide / eligibility / rag / ingestion-enrichment 4개 모듈에 각각 `*ReprocessEventListener` 추가. 기존 `*GenerationEventListener` 와 거의 동일한 코드 (이벤트 타입만 다름).

- **장점**: 모듈 경계 엄격 유지. 각 모듈이 자신의 재처리 진입점 소유.
- **단점**: 4개 파일 신규 + 코드 중복 큼. 새 단계 추가 시 4곳 동시 수정.

### 옵션 B — admin 모듈에 통합 listener (1 file) ★ 추천

`admin.application.listener.PolicyReprocessRequestedEventListener` 하나가 이벤트를 받아 4단계 순차 처리. 각 단계는 이미 admin service 가 의존하는 application service 를 직접 호출 (`RagIndexingService`, `GuideGenerationService`, `EligibilityRuleGenerationService`, `AttachmentReindexService`).

- **장점**: 가장 적은 변경 (admin 모듈만). 기존 `AdminPolicyProcessingService.retryStep` 의 단계별 호출 패턴과 일관. event 에 들어있는 `stepIds` 를 listener 가 받아 markFinished 만 호출 → 트랜잭션 짧음.
- **단점**: admin 모듈이 guide / eligibility / rag service 에 의존 (이미 그렇게 되어 있으므로 추가 부담 없음).

### 옵션 C — PolicyUpsertedEvent 재발행 (기존 listener 재사용)

listener 가 `PolicyReprocessRequestedEvent` 를 받아 `PolicyUpsertedEvent` 재발행 → 기존 GuideGenerationEventListener / EligibilityRuleGenerationEventListener / RagIndexingEventListener 가 자동 처리.

- **장점**: listener 코드 거의 없음 (이벤트 변환만).
- **단점**: 기존 listener 들이 또 `markStarted` 호출 → admin service 가 이미 만든 stepIds 와 별개로 새 step 행 생성 (attempt 카운터 이중 증가). 의미가 "신규 적재" 와 "재처리" 가 섞임. 디버깅 어려움.

→ **옵션 B 채택**.

## 3. 아키텍처

```
[운영자]
  ↓ POST /api/v1/admin/policies/processing/{id}/reprocess
[AdminPolicyProcessingService.reprocess]
  ↓ stepService.markStarted(ENRICHMENT/GUIDE/RULE/RAG) × 4 → stepIds
  ↓ eventPublisher.publishEvent(PolicyReprocessRequestedEvent(policyId, reason, stepIds))
  ↓ ResponseEntity.ok(ReprocessResponse(queued=true, stepIds, ...))   [HTTP 응답 즉시 반환]
[Spring ApplicationEventPublisher]
  ↓ @Async("llmExecutor")  (기존 llmExecutor 빈 재사용)
[PolicyReprocessRequestedEventListener.onPolicyReprocessRequested]
  ├─ ENRICHMENT  step → markFinished(SKIPPED, "MVP: ENRICHMENT manual trigger 미연결")
  ├─ GUIDE       step → guideGenerationService.generateGuide(GenerateGuideCommand) → markFinished
  ├─ RULE        step → eligibilityRuleGenerationService.generateRules(GenerateEligibilityRulesCommand) → markFinished
  └─ RAG_INDEXING step → ragIndexingService.indexPolicyDocument(IndexPolicyDocumentCommand) → markFinished
```

핵심 결정 (브레인스토밍 확정):
- **비동기 (@Async)**: HTTP 응답이 LLM 응답 대기에 블록되지 않도록 listener 메서드에 `@Async("llmExecutor")` 적용. 기존 `common.config.AsyncConfig.llmExecutor` 빈 재사용 (`core 2 / max 4 / queueCapacity 100 / CallerRunsPolicy`). 신규 Executor 빈 추가 불필요.
- **stepIds 활용**: event 의 `List<Long> stepIds` 인덱스 (`[0]=ENRICHMENT, [1]=GUIDE, [2]=RULE, [3]=RAG_INDEXING`) 로 markFinished 의 stepRowId 매칭. `AdminPolicyProcessingService.reprocess` 가 `List.of(ENRICHMENT, GUIDE, RULE, RAG_INDEXING)` 으로 순회하며 stepIds 를 채우므로 코드 레벨에서 순서가 보장됨. listener 는 새 markStarted 호출 없음.
- **단계 독립 처리**: 한 단계 실패해도 다음 단계 계속. 각 단계는 자체 try/catch. RAG_INDEXING 실패가 GUIDE 결과를 무효화하지 않는다.
- **순차 실행**: ENRICHMENT → GUIDE → RULE → RAG_INDEXING. 병렬 실행은 의존성이 없어도 LLM API 동시 호출 폭주 위험 + 디버깅 복잡도 증가 → MVP 에선 순차.
- **Stale 행 정리는 scheduler 로 자동화**: 운영 환경에 IN_PROGRESS 로 남아있는 step 행 (listener 미구현 시점) 은 수동 SQL 대신 `PolicyProcessingStepTimeoutScheduler` 가 1분마다 검사해 10분 초과 행을 FAILED 로 마감. 기존 `EnrichmentJobTimeoutScheduler` 패턴 그대로.

## 4. 변경 사항

### 4.1 신규 파일

```
backend/src/main/java/com/youthfit/admin/application/listener/
└── PolicyReprocessRequestedEventListener.java

backend/src/main/java/com/youthfit/policy/infrastructure/scheduler/
└── PolicyProcessingStepTimeoutScheduler.java
```

골격 (`AdminPolicyProcessingService.retryStep` 의 호출 패턴 그대로):
```java
@Component
@RequiredArgsConstructor
public class PolicyReprocessRequestedEventListener {

    private final PolicyRepository policyRepository;
    private final PolicyProcessingStepService stepService;
    private final RagIndexingService ragIndexingService;
    private final GuideGenerationService guideGenerationService;
    private final EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @Async("llmExecutor")
    @EventListener
    public void onPolicyReprocessRequested(PolicyReprocessRequestedEvent event) {
        Policy policy = policyRepository.findById(event.policyId()).orElse(null);
        List<Long> ids = event.stepIds();
        if (policy == null) {
            ids.forEach(id -> stepService.markFinished(id, FAILED, "정책 없음", null));
            return;
        }
        // 인덱스 매칭: [0]=ENRICHMENT, [1]=GUIDE, [2]=RULE, [3]=RAG_INDEXING
        finishEnrichment(ids.get(0));
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
            stepService.markFinished(stepRowId, SUCCESS, null, null);
        } catch (Exception e) {
            stepService.markFinished(stepRowId, FAILED, e.getMessage(), null);
        }
    }

    private void finishEnrichment(Long stepRowId) {
        // retryStep(ENRICHMENT) 와 동일한 reason 으로 통일.
        stepService.markFinished(stepRowId, SKIPPED, "MVP: ENRICHMENT manual trigger 미연결", null);
    }
}
```

### 4.2 Async 설정 — 기존 빈 재사용 (신규 파일 없음)

`common.config.AsyncConfig` 가 이미 `@EnableAsync` + `llmExecutor` (`core 2 / max 4 / queueCapacity 100 / CallerRunsPolicy / threadNamePrefix=llm-`) 를 등록하고 있다. 가이드/룰/RAG 후속 처리용으로 만들어진 풀이므로 어드민 재처리도 동일 풀에서 throttle 되는 게 자연스럽다 → `@Async("llmExecutor")` 만 명시.

> CallerRunsPolicy 때문에 큐가 가득 차면 listener 호출 스레드 (Spring 의 이벤트 디스패치 스레드) 가 직접 실행. 동기 호출의 부작용은 admin service 가 이미 트랜잭션을 닫은 상태에서 호출되므로 트랜잭션 점유는 발생하지 않는다.

### 4.3 stepIds 매칭 — List + 인덱스 확정

`AdminPolicyProcessingService.reprocess` (line 427-435) 가 명시적으로 `List.of(ENRICHMENT, GUIDE, RULE, RAG_INDEXING)` 순서로 markStarted 를 호출하며 stepIds 에 add 하므로 listener 의 `ids.get(0..3)` 매칭은 코드 레벨에서 안전. Event 시그니처 변경 / Map 도입 / 재조회 모두 불필요.

만약 향후 단계 추가 (예: AGREGGATE_5) 가 발생하면 이 인덱스 계약을 깨지 않도록 `AdminPolicyProcessingService.reprocess` 의 단계 List 와 `PolicyReprocessRequestedEventListener` 를 같은 PR 에서 함께 수정해야 한다 (구현 PR description 에 명시).

### 4.4 신규 파일 — stale step 행 자동 정리 scheduler

`backend/src/main/java/com/youthfit/policy/infrastructure/scheduler/PolicyProcessingStepTimeoutScheduler.java`:
- 패턴: 기존 `EnrichmentJobTimeoutScheduler` 와 동일.
- `@Scheduled(fixedDelayString = "${policy.processing-step.timeout.fixed-delay-ms:60000}")` — 1분 주기.
- `TIMEOUT = Duration.ofMinutes(10)` — listener 가 4단계 LLM 호출을 끝내는 데 충분한 여유. enrichment 의 5분보다 더 길게.
- `PolicyProcessingStepRepository.findActiveStaleBefore(LocalDateTime threshold)` 신규 메서드 추가 (status=IN_PROGRESS AND started_at < threshold).
- `PolicyProcessingStep` 도메인 메서드 `markTimedOut(LocalDateTime now)` 추가 — 내부에서 status=FAILED, finishedAt=now, reason="timeout" 설정.
- `Clock` 빈 주입 (이미 `EnrichmentJobTimeoutScheduler` 가 쓰고 있음).
- `@EnableScheduling` 은 `user.infrastructure.scheduler.SchedulingConfig` 에서 이미 활성화돼 있어 추가 작업 없음.

이 scheduler 는 두 가지 역할:
1. **운영 환경 마이그레이션**: PR 머지 후 자동으로 기존 stale 행 (listener 미구현 시점에 IN_PROGRESS 로 굳어진 행) 정리. 별도 수동 SQL 불필요.
2. **상시 안전장치**: listener 자체 NPE / OOM / kill -9 등으로 markFinished 가 실행되지 않은 경우에도 1분~10분 안에 status 가 FAILED 로 정정되어 운영자가 재처리 가능.

## 5. 에러 처리

- **정책이 삭제된 후 재처리 이벤트 도착**: 모든 stepIds 를 FAILED 로 마감 ("정책 없음"). listener 단일 try/catch.
- **단일 단계 실패**: 해당 step 만 FAILED, 다음 단계 계속. reason 에 exception message 기록.
- **listener 자체 예외 (NPE 등)**: Spring 의 `AsyncUncaughtExceptionHandler` 로 catch + 로그. step 행은 IN_PROGRESS 로 남음 (재처리 버튼 또 누르면 새 attempt).
- **외부 LLM API 호출 실패 (timeout, rate limit)**: 각 단계의 try/catch 에서 FAILED 처리. retry 는 운영자의 단일 단계 재실행 (`/steps/{step}/retry`) 으로 위임.

## 6. 테스트 전략

### 단위 테스트 (Mockito)
- **PolicyReprocessRequestedEventListenerTest** (신규):
  - 이벤트 수신 시 4개 service 메서드 호출 순서 검증 (InOrder)
  - ENRICHMENT 단계가 SKIPPED 로 마감되는지
  - GUIDE 실패해도 RULE, RAG 계속 진행
  - 정책이 없으면 모든 stepIds 가 FAILED 로 마감

### 통합 테스트
- AdminPolicyProcessingService.reprocess 호출 → event 발행 → listener 가 받아 markFinished 까지 완료 검증.
- @SpringBootTest + @MockitoBean 으로 외부 service 들 mock. step 행이 SUCCESS/SKIPPED 로 마감됨을 DB 에서 확인.

### 회귀
- 본 PR (#127) 의 `reprocess_queuesAllFourSteps` 같은 기존 테스트는 변경 없이 통과 (admin service 동작 그대로).

## 7. 성공 기준

1. 운영자가 "전체 재처리" 버튼 클릭 후 5초 내 화면에 step 행 status 가 IN_PROGRESS → SUCCESS/FAILED/SKIPPED 로 갱신된다.
2. step 행의 `finished_at` 이 null 인 채로 남아있지 않는다.
3. GUIDE / RULE / RAG_INDEXING 단계가 각각 LLM/임베딩 API 를 실제 호출한다 (반영된 chunk / guide / rule 데이터가 DB 에 새로 들어간다).
4. ENRICHMENT 단계는 SKIPPED 로 마감되며 reason 에 "MVP: ENRICHMENT manual trigger 미연결" 가 명시된다 (`retryStep(ENRICHMENT)` 의 메시지와 동일).
5. HTTP `POST /reprocess` 응답이 50ms 내 (LLM 응답 대기 안 함).
6. 한 단계 실패가 다음 단계 진행을 막지 않는다.

## 8. 비기능 / 운영

- **Async thread pool**: 기존 `llmExecutor` (`core 2 / max 4 / queueCapacity 100 / CallerRunsPolicy`) 재사용. 가이드/룰/RAG 일반 후속 처리와 풀을 공유하므로 LLM API rate limit 이 공통 throttle 로 작용. 운영자가 동시에 100개 정책 재처리 요청 → queue 에 쌓여 순차 처리, 그 이상은 CallerRunsPolicy 가 이벤트 디스패치 스레드에서 직접 실행해 자연 throttle.
- **비용 방어**: 기존 `CostGuard` 가 RAG/GUIDE/RULE service 내부에 이미 적용되어 있다면 listener 가 별도 가드 둘 필요 없음. 없다면 listener 진입 시 cost 검사 추가 (plan 단계에서 확인).
- **모니터링**: admin LLM 비용 대시보드에서 재처리 사유 (`event.reason()`) 별 token 소비량 추적 가능 (`step.detail_json` 에 reason 기록).
- **rate limit / debounce**: 같은 정책에 대해 5분 내 재처리 요청은 거부 (admin service.reprocess 측에서 검증). 본 spec 범위 외, 별도 follow-up.

## 9. 마이그레이션

DB 스키마 변경 없음. 기존 `policy_processing_step` 테이블 그대로 사용.

PR #127 이 머지된 시점부터 listener 미구현으로 IN_PROGRESS 로 남아있는 step 행이 있을 수 있다 (운영자가 "전체 재처리" 버튼을 눌렀다면). 본 PR 머지와 동시에 신규 `PolicyProcessingStepTimeoutScheduler` 가 활성화되어 다음을 자동 수행:

- 1분 주기 polling
- `status = IN_PROGRESS AND started_at < NOW() - 10 minutes` 인 행을 `markTimedOut(now)` 로 FAILED 마감 (reason="timeout")
- 운영자 개입 없음. 첫 fixed-delay tick (배포 후 최대 1분) 안에 stale 행 일괄 정리.

운영자는 따로 SQL 을 돌릴 필요 없고, 배포 직후 대시보드에서 "처리 중…" 표시가 사라지는 것만 확인.

## 10. 다음 단계 (plan 으로 인계)

브레인스토밍에서 다음이 확정됨:
- ✅ AsyncConfig: 기존 `llmExecutor` 재사용 (신규 빈 없음)
- ✅ Service 시그니처: `retryStep` 패턴 그대로 — `GenerateGuideCommand` / `GenerateEligibilityRulesCommand` / `IndexPolicyDocumentCommand` 사용
- ✅ stepIds: `List<Long>` + 인덱스 (admin service 가 코드 레벨에서 순서 보장)
- ✅ ENRICHMENT: SKIPPED 마감, reason 은 retryStep 과 동일한 "MVP: ENRICHMENT manual trigger 미연결"
- ✅ Stale 정리: `PolicyProcessingStepTimeoutScheduler` 신규 (수동 SQL 없음)

남은 구현 phase:
1. **Phase 1 — Listener 구현**:
   - `admin/application/listener/PolicyReprocessRequestedEventListener.java` 신규
   - 단위 테스트: 4개 service 호출 순서 (InOrder), GUIDE 실패 시 RULE/RAG 계속, policy 없을 때 4 stepIds 전부 FAILED, ENRICHMENT SKIPPED reason 검증
2. **Phase 2 — Stale scheduler 구현**:
   - `PolicyProcessingStep.markTimedOut(LocalDateTime now)` 도메인 메서드 추가
   - `PolicyProcessingStepRepository.findActiveStaleBefore(LocalDateTime threshold)` 신규
   - `policy/infrastructure/scheduler/PolicyProcessingStepTimeoutScheduler.java` 신규
   - 단위 테스트: threshold 보다 오래된 IN_PROGRESS 만 FAILED 로 전환, threshold 이후 행은 그대로
3. **Phase 3 — 통합 테스트**:
   - `AdminPolicyProcessingService.reprocess` → event → listener → markFinished 까지의 end-to-end (@SpringBootTest + @MockitoBean 으로 4 service mock)
   - DB 에서 step 행이 SUCCESS/SKIPPED 로 마감되는 것 검증
4. **검증**: 정책 1건 수동 재처리 → 어드민 대시보드에서 5초 내 step status 갱신 확인
