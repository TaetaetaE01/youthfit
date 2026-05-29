# PolicyReprocessRequestedEvent Listener 구현 설계

> **상태**: spec (2026-05-29 작성)
> **선행**: [DONE_2026-05-29-admin-policy-processing-dashboard-design.md](./DONE_2026-05-29-admin-policy-processing-dashboard-design.md) — `PolicyReprocessRequestedEvent` 발행 측 구현 완료
> **모듈**: `admin` (listener 추가), `guide` / `eligibility` / `rag` (기존 service 재사용)
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
  ↓ (비동기 @Async)
[PolicyReprocessRequestedEventListener.onPolicyReprocessRequested]
  ├─ ENRICHMENT  step → markFinished(SKIPPED, "MVP: n8n trigger 미연결")
  ├─ GUIDE       step → guideGenerationService.generateGuide(policy) → markFinished
  ├─ RULE        step → eligibilityRuleGenerationService.generateRules(policy) → markFinished
  └─ RAG_INDEXING step → ragIndexingService.indexPolicyDocument(...) → markFinished
```

핵심 결정:
- **비동기 (@Async)**: HTTP 응답이 LLM 응답 대기에 블록되지 않도록 listener 메서드에 `@Async` 적용. Spring 의 `TaskExecutor` 빈 필요 (이미 있는지 확인 필요. 없으면 신규 `@Configuration` 클래스로 `ThreadPoolTaskExecutor` 빈 등록 — `core 2 / max 4 / queueCapacity 50` 정도).
- **stepIds 활용**: event 에 들어있는 `List<Long> stepIds` 의 인덱스 (`[0]=ENRICHMENT, [1]=GUIDE, [2]=RULE, [3]=RAG_INDEXING`) 로 markFinished 의 stepRowId 매칭. listener 는 새 markStarted 호출 없음 (admin service 가 이미 만듦).
- **단계 독립 처리**: 한 단계 실패해도 다음 단계 계속. 각 단계는 자체 try/catch. RAG_INDEXING 실패가 GUIDE 결과를 무효화하지 않는다.
- **순차 실행**: ENRICHMENT → GUIDE → RULE → RAG_INDEXING. 병렬 실행은 의존성이 없어도 LLM API 동시 호출 폭주 위험 + 디버깅 복잡도 증가 → MVP 에선 순차.

## 4. 변경 사항

### 4.1 신규 파일

```
backend/src/main/java/com/youthfit/admin/application/listener/
└── PolicyReprocessRequestedEventListener.java
```

골격:
```java
@Component
@RequiredArgsConstructor
public class PolicyReprocessRequestedEventListener {

    private final PolicyRepository policyRepository;
    private final PolicyProcessingStepService stepService;
    private final RagIndexingService ragIndexingService;
    private final GuideGenerationService guideGenerationService;
    private final EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @Async
    @EventListener
    public void onPolicyReprocessRequested(PolicyReprocessRequestedEvent event) {
        Policy policy = policyRepository.findById(event.policyId()).orElse(null);
        if (policy == null) {
            // 모든 stepIds 를 FAILED 로 마감
            event.stepIds().forEach(id -> stepService.markFinished(id, FAILED, "정책 없음", null));
            return;
        }
        List<Long> ids = event.stepIds();
        finishEnrichment(ids.get(0));                            // SKIPPED (MVP)
        runWithStep(ids.get(1), () -> guideGenerationService.generateGuide(policy));
        runWithStep(ids.get(2), () -> eligibilityRuleGenerationService.generateRules(policy));
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
        stepService.markFinished(stepRowId, SKIPPED, "MVP: ENRICHMENT 재처리는 n8n 재크롤 필요", null);
    }
}
```

### 4.2 신규 파일 — Async 설정 (이미 있으면 skip)

`backend/src/main/java/com/youthfit/common/config/AsyncConfig.java`:
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "reprocessTaskExecutor")
    public Executor reprocessTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("reprocess-");
        executor.initialize();
        return executor;
    }
}
```

기존에 `@EnableAsync` 가 적용되어 있는지 먼저 확인. 있으면 신규 Configuration 클래스 불필요, `@Async("reprocessTaskExecutor")` 명시만.

### 4.3 수정 파일 — admin service.reprocess 의 stepIds 순서 보장

기존 코드 (`AdminPolicyProcessingService.reprocess`) 가 markStarted 호출 순서를 `ENRICHMENT → GUIDE → RULE → RAG_INDEXING` 로 보장하는지 검증. 보장한다면 변경 없음. 만약 List 순서가 다르면 listener 의 `ids.get(0)` 매칭이 깨지므로 Map 으로 전달 권장:

```java
// event 시그니처를 변경하거나, listener 가 stepIds 와 별도로 policyId 로 다시 step row 조회 (안전):
public void onPolicyReprocessRequested(PolicyReprocessRequestedEvent event) {
    // 각 ProcessingStep 의 최신 IN_PROGRESS 행 조회 (markStarted 직후 = attempt 최대)
    Map<ProcessingStep, Long> stepRowMap = stepService.findInProgressRowsByPolicyId(event.policyId());
    // ...
}
```

→ **결정 필요 (spec 검토 시)**: 순서 보장으로 갈지 / Map 으로 갈지. 안전성 측면에선 Map. 변경 범위는 List + 인덱스가 적음. 본 spec 은 일단 List + 인덱스로 두되 plan 단계에서 재검토.

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
4. ENRICHMENT 단계는 SKIPPED 로 마감되며 reason 에 "MVP: ENRICHMENT 재처리는 n8n 재크롤 필요" 가 명시된다.
5. HTTP `POST /reprocess` 응답이 50ms 내 (LLM 응답 대기 안 함).
6. 한 단계 실패가 다음 단계 진행을 막지 않는다.

## 8. 비기능 / 운영

- **Async thread pool 적정 크기**: `core 2 / max 4 / queueCapacity 50`. LLM API rate limit 고려 (운영자가 동시에 50개 정책 재처리 요청해도 queue 에 쌓여 순차 처리).
- **비용 방어**: 기존 `CostGuard` 가 RAG/GUIDE/RULE service 내부에 이미 적용되어 있다면 listener 가 별도 가드 둘 필요 없음. 없다면 listener 진입 시 cost 검사 추가 (plan 단계에서 확인).
- **모니터링**: admin LLM 비용 대시보드에서 재처리 사유 (`event.reason()`) 별 token 소비량 추적 가능 (`step.detail_json` 에 reason 기록).
- **rate limit / debounce**: 같은 정책에 대해 5분 내 재처리 요청은 거부 (admin service.reprocess 측에서 검증). 본 spec 범위 외, 별도 follow-up.

## 9. 마이그레이션

DB 스키마 변경 없음. 기존 `policy_processing_step` 테이블 그대로 사용.

PR #127 이 머지된 시점부터 listener 미구현으로 IN_PROGRESS 로 남아있는 step 행이 있을 수 있다 (운영자가 "전체 재처리" 버튼을 눌렀다면). 본 spec 구현 PR 머지 전:
- 운영 환경에서 `SELECT * FROM policy_processing_step WHERE status = 'IN_PROGRESS' AND started_at < NOW() - INTERVAL '1 hour'` 로 stale 행 조회
- 수동으로 `UPDATE ... SET status = 'FAILED', finished_at = NOW(), reason = 'listener 미구현 시점의 stale 행' WHERE ...` 로 정리

## 10. 다음 단계 (plan 으로 인계)

1. **discovery**: `@EnableAsync` / `ThreadPoolTaskExecutor` 빈이 이미 존재하는지 확인 → AsyncConfig 신규 vs 재사용 결정
2. **discovery**: `GuideGenerationService.generateGuide(Policy)` / `EligibilityRuleGenerationService.generateRules(Policy)` 의 정확한 public method 시그니처 확인
3. **결정**: stepIds 순서 보장 (List + index) vs Map<Step, Long> 으로 전달. 안전성과 변경 범위 trade-off
4. **구현**:
   - Phase 1: listener 신규 + AsyncConfig (필요시) + 단위 테스트
   - Phase 2: 통합 테스트 + stale 행 정리 SQL 스크립트
5. **검증**: 정책 1건 수동 재처리 → 5단계 dot 모두 SUCCESS 또는 SKIPPED 로 마감 확인
