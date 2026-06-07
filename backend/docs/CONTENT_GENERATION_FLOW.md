# Content Generation Flow — 가이드 · 적합도 룰 · RAG · Q&A

> **목적**: 정책 데이터가 DB 에 들어온 이후, AI 기반 콘텐츠(가이드 · 적합도 룰 · RAG 인덱스 · Q&A)가 어떻게 만들어지는지 정리한다.
> **선행 문서**: [INGESTION_PIPELINE.md](./INGESTION_PIPELINE.md) — n8n 부터 정책 DB 적재까지.

---

## TL;DR

```
                ┌─ PolicyUpsertedEvent ─┐
                │                       │
[정책 적재] ────┤                       ├─→ [가이드 생성]
                │                       ├─→ [적합도 룰 생성]
                │                       └─→ [첨부 다운로드 시작 (별도 트랙)]
                ↓
       (첨부 트랙)
       [다운로드] → [텍스트 추출] → [정책 전체 RAG 재인덱싱]
                                            │
                                            ↓
                                ┌─ PolicyAttachmentReindexedEvent ─┐
                                │                                   │
                                ├─→ [가이드 재생성] (이번엔 청크 포함)
                                └─→ [적합도 룰 재추출] (이번엔 청크 포함)

[사용자가 정책 질문]
       ↓
[Q&A SSE 스트리밍] ── RAG 청크 검색 ── LLM 호출 ── 캐시 적재
```

전체는 **2 개의 도메인 이벤트** 로 fan-out 된다:

| 이벤트 | 발행 시점 | 구독자 |
|--------|-----------|--------|
| `PolicyUpsertedEvent` | 정책 신규/변경 적재 직후 | `GuideGenerationEventListener`, `EligibilityRuleGenerationEventListener` |
| `PolicyAttachmentReindexedEvent` | 첨부 추출 완료 후 RAG 인덱싱이 실제로 갱신됐을 때 | `GuideGenerationEventListener`, `EligibilityRuleGenerationEventListener`, `PeriodBackfillService` |

> **핵심 패턴**: 첨부 처리는 오래 걸려서, "본문만 가지고 1차 생성" → "첨부 추출 후 2차 재생성" 의 **2 단계 fan-out** 구조다.

---

## 0. 공통 인프라

### 0-1. 이벤트 리스너 규약

모든 콘텐츠 생성 리스너는 다음 패턴을 따른다:

```java
@Async("llmExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void onPolicyUpserted(PolicyUpsertedEvent event) { ... }
```

- **`AFTER_COMMIT`** — 정책 트랜잭션 커밋 후에만 실행 (DB 에 있는 정책을 읽음)
- **`@Async("llmExecutor")`** — LLM 전용 스레드 풀에서 비동기 실행 (수신 API 응답 지연 X)
- **`fallbackExecution = true`** — 트랜잭션 밖에서 발행된 경우도 처리

### 0-2. CostGuard — LLM 비용 방어

`CostGuard` 빈이 모든 LLM 호출 진입점에 박혀 있다.

```java
if (!costGuard.allows(policyId)) {
    costGuard.logSkip("generateGuide", policyId);
    return new GuideGenerationResult(policyId, false, "cost-guard: allowlist 외 정책");
}
```

- 개발/스테이징 환경에서 모든 정책에 대해 LLM 을 돌리지 않도록 **allowlist 기반 차단**
- 프로덕션은 allowlist 가 비어 있어 전부 통과
- 사용처: `GuideGenerationService`, `EligibilityRuleGenerationService`, `RagIndexingService`, `AttachmentReindexService`, `QnaService`

### 0-3. 변경 감지 — `source_hash`

가이드와 룰은 **입력 콘텐츠 SHA-256 해시** 로 변경 여부를 판단한다.

| 콘텐츠 | 해시 입력 | 비교 컬럼 |
|--------|-----------|-----------|
| RAG 인덱스 | 본문 + enrichment | `policy_document.source_hash` (청크 row 별 동일) |
| 가이드 | 정책 본문 + RAG 청크 + 중위소득 reference + prompt/annotator 버전 | `guide.source_hash` |
| 적합도 룰 | 정책 본문 + 첨부 청크 + prompt 버전 | `eligibility_rule.source_hash` (룰 row 별 동일) |

해시가 같으면 **재생성을 스킵**한다. 프롬프트가 바뀌면 버전 상수를 올려서 모든 정책을 강제로 다시 만들 수 있다.

---

## 1. 가이드 생성 (Guide)

### 1-1. 트리거

```
[PolicyUpsertedEvent]                    [PolicyAttachmentReindexedEvent]
       ↓                                          ↓
[GuideGenerationEventListener.onPolicyUpserted]  [.onAttachmentReindexed]
       ↓                                          ↓
       └─────────────→ guideGenerationService.generateGuide(policyId)
```

### 1-2. `GuideGenerationService.generateGuide()` 흐름

```
① cost-guard 확인                       (allowlist 외면 스킵)
   ↓
② 정책 로드 + RAG 청크 조회             (chunks = policy_document)
   ↓
③ 중위소득 reference 로드               (referenceYear 기반 yaml → fallback: 최신)
   ↓
④ source_hash 계산
   = sha256(title + summary + body
            + 모든 청크 + reference.year/version
            + enrichment.fetchedAt
            + "prompt:v5" + "annotator:v4")
   ↓
⑤ 기존 guide.source_hash 와 비교
   → 같으면 스킵하고 종료
   ↓
⑥ LLM 호출 (gpt-4o-mini, max 2048 tokens)
   [GuideLlmProvider.generateGuide(input)]
   → GuideContent { highlights, target, criteria, content,
                    applyMethod, deadlineNote, requiredDocuments,
                    contact, pitfalls }
   ↓
⑦ GuideValidator.validate()
   - 환각 검출, 친근체 검출, 출처 라벨 검증
   → 위반 발견 시 ⑧ 으로
   ↓
⑧ 재시도 (피드백 첨부 LLM 재호출)
   → 위반이 줄어들면 2 차 응답 채택
   → 안 줄어들면 1 차 응답 유지
   ↓
⑨ IncomeBracketAnnotator.annotate()      (결정성 후처리)
   - "중위소득 100%" → "1인 가구 222 만원" 같은 만원 단위 환산값 보강
   ↓
⑩ enforceAttachmentSourceField()
   - attachmentRef 있는데 sourceField 라벨이 잘못 박힌 케이스 보정
   ↓
⑪ filterInvalidSourceFields()
   - 정책에 없는 필드(예: ENRICHMENT 가 없는데 ENRICHMENT 라벨) 가진 항목 폐기
   ↓
⑫ Guide 엔티티 저장 (신규 또는 regenerate)
```

### 1-3. 가이드 콘텐츠 구조

```
GuideContent
├─ oneLineSummary       — 한 줄 요약
├─ highlights []        — 강조 포인트 (각각 sourceField, attachmentRef 동반)
├─ target               — 지원 대상 풀이
├─ criteria             — 선정 기준 풀이
├─ content              — 지원 내용 풀이
├─ applyMethod          — 신청 방법 (list section)
├─ deadlineNote         — 마감/일정 (list section)
├─ requiredDocuments    — 필요 서류 (list section)
├─ contact              — 문의처 (list section)
└─ pitfalls []          — 자주 놓치는 함정 (sourceField, attachmentRef 동반)
```

각 항목은 `GuideSourceField` 라벨로 출처를 표시:
- `SUPPORT_TARGET`, `SELECTION_CRITERIA`, `SUPPORT_CONTENT`, `BODY` — 정책 본문 필드
- `ATTACHMENT` — 첨부에서 추출
- `ENRICHMENT` — enrichment 파이프라인에서 보강된 정보

### 1-4. 검증 / 후처리 정책

| 항목 | 검증 / 후처리 | 위반 시 |
|------|---------------|---------|
| 환각 (원문에 없는 숫자 토큰) | 후처리 단계에서 로그 경고 | 저장은 하되 운영팀이 확인 |
| 친근체 ("~해요" 등) | 검출 시 로그 경고 | 저장은 하되 운영팀이 확인 |
| `sourceField` 라벨 무효 | 해당 항목 폐기 | 저장 |
| `attachmentRef.attachmentId` 가 정책 첨부 화이트리스트 외 | 해당 항목 폐기 | 저장 |
| 검증 1·2 위반 (재시도 트리거) | LLM 1 회 재호출 | 2 차 응답이 더 나으면 채택 |

---

## 2. 적합도 룰 생성 (Eligibility)

적합도 룰은 **두 가지 경로**가 있다:

1. **결정성(Deterministic) 추출** — n8n 이 코드(0013001, 0049001 등)를 보낸 경우, 룰 베이스로 매핑
2. **LLM 추출** — 코드가 없는 경우 본문/첨부를 LLM 으로 분석

### 2-1. 결정성 추출 (선호)

```
[IngestionService.receivePolicy()]
       ↓ (n8n 이 rawCodes 를 보낸 경우만)
[CodeBasedRuleExtractionService.extractAndPersist()]
       ↓
[CodeBasedRuleExtractor.extract()]
       ↓
8 개 룰 생성: age, maritalStatus, annualIncome, employmentKind,
              education, majorField, specializationField, region
       ↓
[eligibility_rule] 테이블 저장 (extraction_version = "code-v1", confidence = HIGH)
```

**코드 → enum 매핑**의 예 (`CodeBasedRuleExtractor` 상수):

| field | 입력 코드 | enum 값 |
|-------|-----------|---------|
| `employmentKind` | `0013001` | `EMPLOYEE` |
| `employmentKind` | `0013003` | `UNEMPLOYED` |
| `education` | `0049005` | `COLLEGE_IN` |
| `majorField` | `0011005` | `ENGINEERING` |
| `region` (zipCode 접두 2 자리) | `11` | `SEOUL` |
| `region` | `41` | `GYEONGGI` |

연령은 `ageMin`/`ageMax` 조합으로 `BETWEEN`/`GTE`/`LTE`/`ANY` 자동 선택. 모든 zipCode 가 17 개 시도를 다 커버하면 `ANY`("전국") 로 격하.

### 2-2. LLM 추출 (fallback)

```
[PolicyUpsertedEvent] 또는 [PolicyAttachmentReindexedEvent]
       ↓
[EligibilityRuleGenerationEventListener]
       ↓
hasCodeBasedRules(policyId)?  → YES 면 스킵 (결정성 룰이 우선)
       ↓ NO
[EligibilityRuleGenerationService.generateRules()]
       ↓
① cost-guard 확인
   ↓
② 정책 + 첨부 청크 로드
   ↓
③ source_hash 계산 = sha256(본문 + 청크 + "prompt:v1")
   → 기존 룰의 hash 와 같고 extraction_version 동일이면 스킵
   ↓
④ LLM 호출 [EligibilityRuleLlmProvider.extractRules()]
   → List<RawExtractedRule>
   ↓
⑤ EligibilityRuleValidator.validate()
   → 위반 시 피드백 첨부 재시도 1 회
   ↓
⑥ 기존 룰 전체 삭제 → 신규 룰 일괄 저장
```

### 2-3. 룰 데이터 모델

```
EligibilityRule
├─ policyId
├─ field              — "age", "annualIncome", "region", ...
├─ operator           — EQ, NEQ, GT, GTE, LT, LTE, BETWEEN, IN, NOT_IN, ANY
├─ value              — "25", "20~30", "SEOUL,GYEONGGI", ...
├─ label              — 사용자에게 보일 한글 라벨 ("연령", "거주지")
├─ sourceReference    — 추출 근거 ("getPlcy.sprtTrgtMinAge: 19")
├─ confidence         — HIGH, MEDIUM, LOW
├─ sourceHash         — 변경 감지용
└─ extractionVersion  — "code-v1" 또는 "v1" (LLM prompt 버전)
```

### 2-4. 적합도 판정 (`EligibilityService.judgeEligibility`)

사용자 요청 시점에 실행되는 **읽기 전용** 평가:

```
[사용자 → POST /api/eligibility/policies/{id}/judge]
       ↓
[EligibilityService.judgeEligibility()]
       ↓
① 사용자 EligibilityProfile 로드 (없으면 빈 프로필)
   ↓
② 정책 + 정책의 모든 EligibilityRule 로드
   ↓
③ EligibilityEvaluator 가 룰별로 evaluateRule(rule, profile)
   → CriterionEvaluation { result, uncertainReason, userValue }
   → result: LIKELY_ELIGIBLE / LIKELY_INELIGIBLE / UNCERTAIN
   ↓
④ 룰별 결과를 CriterionResult 로 변환
   - RequirementFormatter: "19~34세" 같은 사람용 텍스트
   - UserValueFormatter: 사용자 값 포맷
   - VerdictTextGenerator: "충족", "미충족", "정보 부족" 등 verdict 문구
   ↓
⑤ 전체 판정
   - LIKELY_INELIGIBLE 한 건이라도 → 전체 LIKELY_INELIGIBLE
   - UNCERTAIN 있고 INELIGIBLE 없음 → 전체 UNCERTAIN
   - 전부 LIKELY_ELIGIBLE → 전체 LIKELY_ELIGIBLE
   ↓
⑥ EligibilityJudgmentResult 반환
   { policyId, title, overall, summary, grouped { ineligible/uncertain/eligible },
     disclaimer }
```

> 정책 해석 원칙(`docs/PRODUCT.md`)에 따라 **확정적 판정 대신 LIKELY/UNCERTAIN** 어휘를 쓴다.

---

## 3. RAG 인덱싱

가이드와 Q&A 가 의존하는 임베딩 인덱스. **2단계 fan-out** 으로 만들어진다.

- **1차 (PolicyUpsertedEvent)**: 정책 ingest 직후 `RagIndexingEventListener` 가 본문 + enrichment 만으로 즉시 인덱싱. 첨부가 0건이거나 첨부 추출이 아직 안 끝난 정책도 이 단계에서 Q&A 가능 상태가 된다.
- **2차 (첨부 추출 완료 후)**: 모든 첨부가 종결 상태가 되면 `AttachmentReindexService` 가 본문 + 첨부 텍스트를 merge 해 재인덱싱. `source_hash` 가 바뀌어 1차 청크가 모두 교체된다.

### 3-1. 1차 트리거 — PolicyUpsertedEvent

`IngestionService.receivePolicy()` 가 정책을 정상 commit 한 직후 `PolicyUpsertedEvent` 가 발행되면 `RagIndexingEventListener.onPolicyUpserted()` 가 `@Async("llmExecutor")` 로 실행되어 본문 + enrichment 만으로 `RagIndexingService.indexPolicyDocument()` 호출. 본문이 비어있는 정책은 스킵.

### 3-2. 2차 트리거 — 첨부 트랙

```
[정책 적재 → IngestionService 가 attachmentDownloadService.downloadForPolicyAsync 호출]
       ↓
[비동기 다운로드 → S3 또는 Local 저장]
       ↓
[AttachmentExtractionScheduler 60 초마다 폴링]
       ↓
[정책의 모든 첨부가 종결 상태 (EXTRACTED/SKIPPED/FAILED-retry-exhausted)]
       ↓
[AttachmentReindexService.reindex(policyId)]
       ↓
① 첨부 ≥2개 시 LLM 게이트(AttachmentEmbeddingJudge) 로 임베딩 가치 선별
   - 이미 판정된 첨부(embedding_included non-null)는 재호출하지 않음 (캐시)
   - embed=false 판정 첨부는 머지에서 제외; 판정 결과를 policy_attachment 에 영속화
   - LLM 실패 시 fail-open: 미판정 첨부 전체를 embedded=true 로 저장 (콘텐츠 손실 방지)
   ↓
② 정책 본문 + 선별된 첨부 텍스트 머지 (cap 200KB)
   - "=== 정책 본문 ===" + body
   - "=== 첨부 attachment-id=N name=... ===" + extracted text (포함 판정만)
   - 페이지 sentinel "\f<page=...>" → "--- page=... ---" 변환
   ↓
③ RagIndexingService.indexPolicyDocument(merged, enrichment)
   - DocumentChunker.computeHash(merged, enrichment) → 신규 hash
   - 기존 청크 hash 와 같으면 스킵
   - 다르면: 기존 청크 삭제 → QnaCacheInvalidator 호출 → 신규 청크 + 임베딩 저장
   ↓
③ 업데이트가 실제로 발생했을 때만
   PolicyAttachmentReindexedEvent 발행
   → 가이드 / 룰 재생성 fan-out
```

### 3-3. `DocumentChunker`

- **분할 기준**: 문단/문장, 최대 500 자
- **메타 보존**: 청크별 `chunkIndex`, `source` (`BODY` / `ATTACHMENT` / `ENRICHMENT_BODY`), `attachmentId`, `pageStart`/`pageEnd`
- **임베딩**: OpenAI `text-embedding-3-small` (1536 차원), `pgvector` 의 `vector(1536)` 컬럼에 저장

### 3-4. 인덱싱 결과

| `policy_document.source` | 의미 |
|--------------------------|------|
| `BODY` | 정책 본문에서 분할 |
| `ATTACHMENT` | 첨부 텍스트에서 분할 (페이지 정보 포함) |
| `ENRICHMENT_BODY` | enrichment 파이프라인이 보강한 본문 청크 |

---

## 4. Q&A (실시간)

Q&A 는 사용자 요청 시점에 동적으로 실행된다. **사전 생성은 없다** — 그러나 캐시는 적극 활용한다.

### 4-1. 전체 흐름 (`QnaService.askQuestion`)

```
[사용자 POST /api/qna/questions (SSE)]
       ↓
[QnaService.askQuestion()]
       ↓
① cost-guard 확인
   ↓
② Policy 로드, QnaHistory IN_PROGRESS 로 기록
   ↓
③ 가상 스레드로 비동기 실행 시작
       ↓
④ ┌──────────────────────────────────┐
   │ ① 정확 일치 캐시 (Redis)         │
   │    qnaAnswerCache.get(...)       │
   │    HIT → 즉시 SSE 송출 후 종료    │
   └──────────────────────────────────┘
       ↓ MISS
⑤ ┌──────────────────────────────────┐
   │ ② 질문 임베딩                     │
   │    embeddingProvider.embed(Q)    │
   └──────────────────────────────────┘
       ↓
⑥ ┌──────────────────────────────────┐
   │ ③ 의미(semantic) 캐시 (pgvector) │
   │    semanticQnaCache.findSimilar() │
   │    분류: HIT / NEAR_MISS / MISS  │
   │    HIT → SSE 송출 후 종료         │
   └──────────────────────────────────┘
       ↓ MISS / NEAR_MISS
⑦ ┌──────────────────────────────────┐
   │ ④ Query rewriting (옵션)         │
   │    가능하면 검색용 질의를 재작성  │
   │    → 임베딩 재계산                │
   └──────────────────────────────────┘
       ↓
⑧ ┌──────────────────────────────────┐
   │ ⑤ RAG 검색                        │
   │    ragSearchService.search(...)  │
   │    pgvector 코사인 + 키워드 boost │
   │    → 청크 N 개                    │
   │    relevance threshold 통과만 사용│
   └──────────────────────────────────┘
       ↓
   청크 0 개 → "이 정책은 본문 인덱싱이 되어있지 않다" 메시지로 종료
       ↓
⑨ ┌──────────────────────────────────┐
   │ ⑥ LLM 스트리밍                    │
   │    qnaLlmProvider.generateAnswer │
   │    gpt-4o-mini, max 1024 tokens   │
   │    chunk → SSE CHUNK 이벤트       │
   └──────────────────────────────────┘
       ↓
   isFallbackAnswer? ("명시되어 있지 않" 패턴 검출)
       - YES → sources 비움, follow-up 생성 스킵
       - NO  → contact footer 첨부, follow-up 생성
       ↓
   SSE SOURCES → SUGGESTIONS → DONE 이벤트
       ↓
⑩ ┌──────────────────────────────────┐
   │ ⑦ 캐시 적재                       │
   │    Redis (정확 일치)              │
   │    pgvector (의미 일치)           │
   │    QnaHistory markCompleted       │
   └──────────────────────────────────┘
```

### 4-2. SSE 이벤트 프로토콜

| 이벤트 type | 페이로드 | 시점 |
|-------------|----------|------|
| `CHUNK` | `{ content: "..." }` | LLM 출력 청크별 (스트리밍) |
| `SOURCES` | `{ sources: [{ policyId, attachmentId, attachmentLabel, pageStart, pageEnd, excerpt }] }` | LLM 완료 후 1 회 |
| `SUGGESTIONS` | `{ questions: [...] }` | follow-up 생성 시 1 회 |
| `DONE` | `{}` | 종료 |
| `ERROR` | `{ content: "..." }` | 실패 시 |

### 4-3. 캐시 무효화

- 새로운 RAG 인덱싱이 발생하면(`RagIndexingService` 내부에서) `QnaCacheInvalidator.invalidatePolicy(policyId)` 호출
- → 정책 단위로 Redis 정확 캐시 + pgvector 의미 캐시 둘 다 무효화

---

## 5. 재실행 / 변경 감지 매트릭스

| 트리거 | 가이드 재생성 | 룰 재추출 | RAG 재인덱싱 | Q&A 캐시 무효화 |
|--------|---------------|-----------|--------------|------------------|
| 정책 본문 변경 | ✅ (hash 다름) | ✅ (hash 다름) | ✅ (hash 다름) | ✅ (RAG 가 무효화) |
| 첨부 추출 완료 | ✅ (청크가 hash 에 포함) | ✅ (청크가 hash 에 포함) | ✅ (merge 결과 hash 다름) | ✅ |
| enrichment 변경 (fetchedAt 갱신) | ✅ (fetchedAt 이 hash 에 포함) | ❌ (enrichment 는 hash 입력 아님) | ✅ | ✅ |
| 프롬프트 버전 상승 | ✅ (PROMPT_VERSION 이 hash 에 포함) | ✅ | ❌ | ❌ |
| 사용자 프로필 변경 | ❌ | ❌ | ❌ | — (적합도만 영향, 룰이 아니라 평가 시점 데이터) |
| n8n 이 코드 변경 (rawCodes) | ❌ | ✅ deterministic 재실행 | ❌ | ❌ |

---

## 6. 코드 위치 빠른 참조

### 이벤트
- `common/event/PolicyUpsertedEvent.java`
- `common/event/PolicyAttachmentReindexedEvent.java`

### 가이드
- `guide/application/listener/GuideGenerationEventListener.java` — 이벤트 구독
- `guide/application/service/GuideGenerationService.java` — 메인 로직
- `guide/application/service/GuideValidator.java` — 환각/친근체/라벨 검증
- `guide/application/service/IncomeBracketAnnotator.java` — 중위소득 환산 후처리
- `guide/application/port/GuideLlmProvider.java` — LLM 포트
- `guide/infrastructure/external/OpenAiChatClient.java` — OpenAI 구현체

### 적합도 룰
- `eligibility/application/listener/EligibilityRuleGenerationEventListener.java`
- `eligibility/application/service/EligibilityRuleGenerationService.java` — LLM 경로
- `eligibility/application/service/CodeBasedRuleExtractionService.java` — deterministic 경로 (`IngestionService` 가 직접 호출)
- `eligibility/domain/service/CodeBasedRuleExtractor.java` — 코드→enum 매핑
- `eligibility/application/service/EligibilityService.java` — 사용자 판정
- `eligibility/domain/service/EligibilityEvaluator.java` — 룰 평가 코어

### RAG
- `ingestion/application/service/AttachmentReindexService.java` — 첨부 머지 + 인덱싱 트리거
- `rag/application/service/RagIndexingService.java` — 청크 생성 + 임베딩
- `rag/domain/service/DocumentChunker.java` — 분할 + hash
- `rag/application/service/RagSearchService.java` — 검색 (Q&A 가 호출)

### Q&A
- `qna/application/service/QnaService.java` — 메인 SSE 처리
- `qna/application/service/QnaCacheLookupClassifier.java` — HIT/NEAR_MISS/MISS 분류
- `qna/application/service/QnaHistoryWriter.java` — 이력 적재
- `qna/application/port/QnaAnswerCache.java` — Redis 정확 캐시 포트
- `qna/application/port/SemanticQnaCache.java` — pgvector 의미 캐시 포트
- `qna/infrastructure/external/OpenAiQnaClient.java` — LLM 스트리밍 구현체
- `qna/infrastructure/external/RedisQnaAnswerCache.java`
- `qna/infrastructure/external/PgVectorSemanticQnaCache.java`

### 공통
- `common/config/CostGuard.java` — LLM allowlist 차단

---

## 7. 자주 묻는 질문

**Q. 가이드와 적합도 룰은 왜 한 번이 아니라 두 번 만들어지나요?**
A. 정책이 처음 들어왔을 땐 본문만 있고, 첨부 추출은 비동기로 별도 시간이 걸립니다. **1 차** 는 본문만으로 빠르게 만들고, 첨부 추출이 끝나면 **2 차** 로 더 풍부한 정보로 재생성합니다. 콘텐츠가 같으면 hash 비교로 2 차는 스킵됩니다.

**Q. 같은 정책에 deterministic 룰 + LLM 룰이 동시에 존재할 수 있나요?**
A. 없습니다. 리스너가 `hasCodeBasedRules(policyId)` 를 먼저 확인해서 `code-v1` 버전 룰이 있으면 LLM 추출을 스킵합니다. n8n 이 코드를 보낸 정책은 deterministic 만, 아니면 LLM 만.

**Q. Q&A 가 답을 못 만드는 경우는 언제인가요?**
A. ① 정책의 `policy_document` 청크가 0 개 (RAG 인덱싱이 아직 안 됨) — "본문 인덱싱이 되어 있지 않다" 메시지 반환. ② 검색은 됐지만 relevance threshold 를 넘는 청크가 없음 — LLM 이 "명시되어 있지 않다" fallback 답변을 내고 sources 는 빈 채로 전달.

**Q. 프롬프트를 바꿨는데 기존 정책에는 언제 반영되나요?**
A. `GuideGenerationService.PROMPT_VERSION` 또는 `EligibilityRuleGenerationService.PROMPT_VERSION` 상수를 올리세요. hash 입력에 포함되므로 모든 정책의 hash 가 달라지고, 다음 트리거 (정책 변경 / 첨부 재인덱싱) 시 재생성됩니다. 즉시 강제는 별도 백필 스크립트 / admin 트리거가 필요합니다.

**Q. LLM 비용이 폭주하지 않게 어떻게 막나요?**
A. ① `CostGuard` 의 allowlist (개발/스테이징에서 유효), ② hash 기반 변경 감지로 중복 호출 차단, ③ Q&A 는 Redis + pgvector **2 단 캐시**, ④ admin 모듈 LLM 비용 대시보드(`docs/ops/cost-snapshot.md` 참조)로 일별 모니터링.

**Q. 적합도 판정 결과가 너무 단정적으로 나오나요?**
A. `EligibilityResult` 는 `LIKELY_ELIGIBLE` / `LIKELY_INELIGIBLE` / `UNCERTAIN` 셋뿐이고, 사용자에게 표시되는 verdict 문구도 `VerdictTextGenerator` 가 "확정"이 아닌 "추정" 어투로 생성합니다. 최종 판정은 공식 신청 채널에서 받도록 disclaimer 가 항상 동반됩니다.
