# Hybrid Map-Reduce 가이드 생성 — Design Spec

**Date:** 2026-05-26
**Status:** Draft
**Owner:** TaetaetaE01
**Related:** `GuideGenerationService`, `OpenAiChatClient`, RAG `policy_document` 청킹

---

## 1. 배경 / 동기

배포 전 통합 테스트(`POLICY_ALLOWLIST` 제거 후 60개 정책 적재)에서 BOKJIRO 30개 중 22개의 가이드 자동 생성이 silent drop 됨.

근본 원인 디버깅 결과 (`Phase 1-3 — systematic-debugging`):

- **첨부 텍스트가 큰 정책일수록 LLM 호출이 항상 실패** — `policy_attachment.extracted_text` 가 142K~881K chars 인 정책 9개의 `generateGuide` 가 모두 500 반환
- **메커니즘**: `GuideGenerationInput.combinedSourceText()` 가 그 정책의 모든 `policy_document` 청크(RAG 인덱싱 용도로 잘려있던 청크 100+개)를 다시 통째로 이어붙여 prompt 에 넣음 → 단일 호출에 70K~440K input tokens
- **2가지 한계 동시 작용:**
  1. **TPM (분당 토큰) 한도 200,000** — 한 호출이 80%+ 점유 시 분 단위 윈도우 내 추가 호출 불가 (429)
  2. **gpt-4o-mini context window 128K input tokens** — 49번/56번 정책 같은 경우 단일 호출이 물리적으로 불가능 (400)

청크 분할은 RAG (Q&A 의 query 기반 top-K 검색) 용도였지만 가이드 단계에서는 그 분할이 무의미하게 모두 합쳐져 prompt 폭주 발생. 설계 미스매치.

## 2. 목표

- **G1**: 첨부 텍스트 크기와 무관하게 모든 정책에 대해 가이드 자동 생성 성공
- **G2**: 가이드 품질(정확도) 손실 최소화 — truncate 같은 정보 영구 손실 회피
- **G3**: 작은 정책 (현재 잘 동작하는 51개 케이스) 회귀 위험 0
- **G4**: gpt-4o-mini context window (128K) 를 초과하는 단일 정책도 처리 가능

### 비목표 (Non-Goals)

- OpenAI tier 상향 (별도 영업 결정)
- `GuideGenerationEventListener` 의 429 retry/backoff 추가 (별도 작업 — 본 spec 은 단일 호출 후속 retry 가 아닌 prompt 분할 전략)
- RAG (Q&A) 청크 검색 흐름 변경
- 가이드 system prompt / 응답 schema 변경 (`GuideContent` 재사용)
- n8n next-page 로직 디버깅 (별도 작업)

## 3. 접근 비교 (의사결정 근거)

| 옵션 | 정확도 | 비용 | 구현 | 채택 여부 |
|---|---|---|---|---|
| Truncate (`sb.setLength(N)` 1줄) | ★★ | 1배 | 1줄 | ❌ 뒷부분 정보 영구 손실 |
| RAG ranking top-N 청크 | ★★★ | 1배 | 작음 | ❌ ranking 잘못된 청크 정보 영구 손실 |
| Map-reduce always (작은 정책도 N+1번) | ★★★★ | 항상 N+1배 | 중간 | ❌ 작은 정책에 불필요 비용 |
| **Hybrid (작은 단일/큰 map-reduce)** | **★★★★★** | **큰 정책만 ~1.5-2배** | **중간** | **✅ 채택** |
| Structured per-section (섹션별 호출) | ★★★★★ | 항상 5-10배 | 큼 | ❌ 오버킬 |

채택 근거:
- 정보 손실 최소화 (G2) + 큰 정책 처리 (G4) 가능
- 작은 정책 코드 경로 무변경 → 회귀 안전 (G3)
- 큰 정책일수록 단일 거대 prompt 의 "lost in the middle" 회피로 오히려 품질 ↑ 기대

## 4. 디자인

### 4.1 컴포넌트 (5개)

| 컴포넌트 | 위치 | 책임 |
|---|---|---|
| `TokenCounter` | `common/util/` | jtokkit 래퍼. `int countTokens(String text, String model)` |
| `GuideGenerationStrategy` (enum) | `guide/application/dto/` | `SINGLE_CALL` \| `MAP_REDUCE` |
| `GuideStrategySelector` | `guide/application/service/` | `GuideGenerationInput → GuideGenerationStrategy` |
| `MapReduceGuideOrchestrator` | `guide/application/service/` | 청크 그룹화 + partial 호출 N번 + merge 호출 1번 |
| `GuideLlmProvider` 확장 | `guide/application/port/` | `generatePartialGuide(...)` + `mergePartialGuides(...)` 추가 |

### 4.2 흐름

```
GuideGenerationService.generateGuide(command)
    │
    ├─ cost-guard 체크 (현행)
    ├─ policyRepository.findById (현행)
    ├─ existing && !hasChanged → skip (현행)
    │
    ▼
GuideGenerationInput input = GuideGenerationInput.of(policy, chunks, reference)
    │
    ▼
strategy = strategySelector.select(input)
    │
    ├──── SINGLE_CALL (input tokens < 80K) ────────────────────┐
    │   guideLlmProvider.generateGuide(input)                    │
    │   guideLlmProvider.regenerateWithFeedback(...) 재시도 분기 │
    │   (현재 코드 100% 그대로)                                  │
    │                                                            ▼
    │                                                       GuideContent
    │                                                            │
    └──── MAP_REDUCE (input tokens ≥ 80K) ────────────────────  │
        mapReduceOrchestrator.generate(input)                    │
            │                                                    │
            ├─ groups = groupChunksByTokenBudget(input.chunks, 60K)
            ├─ partial[i] = guideLlmProvider.generatePartialGuide(input.withGroup(groups[i])) for i in 0..N-1
            │   (각 그룹별 별도 호출, 순차 — rate limit 회피)
            ├─ final = guideLlmProvider.mergePartialGuides(input.meta, partial[0..N-1])
            └─ return final ────────────────────────────────────┘
    │
    ▼
GuideGenerationService 후속 처리 (validator, annotator, sourceField 보정, save) — 현행 그대로
```

### 4.3 임계값 / Budget

- **분기 임계값**: `combinedSourceText()` input tokens **≥ 80K** → MAP_REDUCE
  - 근거: gpt-4o-mini context window 128K. system prompt (~2K) + meta (~3K) + 응답 max_tokens (~10K) 여유 두고 60% 한도.
- **그룹 token budget**: 각 그룹의 청크 합산 input tokens **≤ 60K**
  - 근거: partial 호출 prompt = system + meta + 그룹 청크. 60K 청크 + 5K meta + 2K system = 67K < 128K context.
- **그룹 수 예상**: 정책 56번 (881K chars ≈ 440K tokens) → 약 7-8 그룹

### 4.4 TokenCounter

- 라이브러리: `com.knuddels:jtokkit:1.1.0` (또는 최신 안정 버전)
- `backend/build.gradle` 의존성 1줄 추가: `implementation 'com.knuddels:jtokkit:1.1.0'`
- 메서드:
  ```java
  public int countTokens(String text, String model);  // model = "gpt-4o-mini" 등
  ```
- 모델별 encoder 매핑:
  - `gpt-4o-mini`, `gpt-4o`, `gpt-4-turbo` → `o200k_base`
  - `text-embedding-3-small/large` → `cl100k_base`
- 캐싱: encoder instance 는 singleton (라이브러리 자체가 thread-safe)

### 4.5 청크 그룹화 알고리즘

```java
List<List<ChunkInput>> groupChunksByTokenBudget(List<ChunkInput> chunks, int budget) {
    List<List<ChunkInput>> groups = new ArrayList<>();
    List<ChunkInput> current = new ArrayList<>();
    int currentTokens = 0;
    for (ChunkInput c : chunks) {
        int t = tokenCounter.countTokens(c.content(), model);
        if (currentTokens + t > budget && !current.isEmpty()) {
            groups.add(current);
            current = new ArrayList<>();
            currentTokens = 0;
        }
        current.add(c);
        currentTokens += t;
    }
    if (!current.isEmpty()) groups.add(current);
    return groups;
}
```

- 단일 청크가 budget 을 단독으로 초과하면 그 청크만 별도 그룹 (truncate 안 함 — partial 호출에서 LLM 이 처리하거나 실패)
- 청크 순서(chunk_index)는 유지 — PDF 페이지 흐름 보존

### 4.6 부분 가이드 (partial) 호출

- **응답 형식**: 풀 `GuideContent` (최종과 동일 schema, validator 재사용)
- **System prompt**: 기존 SYSTEM_PROMPT 를 그대로 사용하되 앞부분에 추가 지시:
  > "이 호출은 정책 본문의 일부 청크 그룹만으로 작성하는 부분 가이드다. 통합 호출에서 다른 부분과 병합될 예정이다. 보이는 내용에 한해서만 응답하라. 누락된 정보를 추측하지 마라."
- **입력**: `GuideGenerationInput` 의 청크 필드만 해당 그룹으로 교체 (정책 메타, body, summary 등은 그대로 유지)

### 4.7 통합 (merge) 호출

- **응답 형식**: 풀 `GuideContent` (최종 결과)
- **System prompt**: 별도 MERGE_SYSTEM_PROMPT
  > "여러 부분 가이드들이 입력으로 주어진다. 중복을 제거하고 우선순위가 높은 항목을 보존하며 최종 가이드를 작성하라.
  > 정책 메타와 부분 가이드들의 정보만 사용한다. 추가 정보를 만들어내지 마라.
  > 출처(sourceField, attachmentRef) 메타는 가능한 보존한다."
- **입력**:
  - 정책 메타 (title, summary, supportTarget 등)
  - 부분 가이드 N개의 JSON 직렬화
- **예상 prompt 크기**: 메타 (~3K tokens) + 부분 가이드 N개 × 평균 4K tokens = N=5 일 때 ~23K tokens. context 안전.

### 4.8 에러 처리

| 시나리오 | 대응 |
|---|---|
| partial 호출 1개 실패 (429, 5xx) | log.warn + 그 그룹 skip. 성공한 partial 만으로 merge 진행 |
| partial 호출 전부 실패 | merge 단계로 가지 못함. 전체 실패 (`generateGuide` 가 throw) |
| merge 호출 실패 | 전체 실패 (현재 단일 호출 실패와 동일) |
| 단일 청크가 60K budget 초과 (희귀) | 그 청크 단독 그룹 → partial 호출. 그 호출이 context 초과로 실패하면 위 1번 시나리오로 처리 |

### 4.9 회귀 안전 (G3)

- `GuideStrategySelector` 가 임계값 미만이면 **`SINGLE_CALL` 반환 → 기존 코드 경로 100% 그대로**
- 51개 가이드 기존 케이스 모두 SINGLE_CALL 분기로 들어감 (각 < 80K tokens 확실)
- 새 컴포넌트는 추가만 됨, 기존 시그니처 불변

### 4.10 비용 영향

| 정책 크기 분포 | 호출 수 | 추가 비용 |
|---|---|---|
| < 80K tokens (예상 85%) | 1번 (현행) | 0 |
| ≥ 80K tokens (예상 15%) | partial N + merge 1 = N+1 | 약 1.5-2배 (system prompt 중복분) |

전체 가이드 생성 누적 비용 예상 증가: 10-15% (작은 정책 비중이 크고, 큰 정책은 어차피 현재는 실패하던 케이스)

## 5. 테스트 전략

| 레벨 | 대상 | 검증 |
|---|---|---|
| 단위 | `TokenCounter` | jtokkit 결과 검증 (알려진 텍스트 → 알려진 토큰 수) |
| 단위 | `GuideStrategySelector` | 임계값 경계 (79K → SINGLE, 80K → MAP_REDUCE) |
| 단위 | `MapReduceGuideOrchestrator` | mockito `GuideLlmProvider` — 그룹화, partial N번 호출, merge 1번 호출 검증 |
| 단위 | `MapReduceGuideOrchestrator` | partial 일부 실패 시나리오 (성공한 것만으로 merge) |
| 통합 (실 LLM 없이) | `GuideGenerationService.generateGuide` | 큰 정책 fixture → MAP_REDUCE 분기 진입 확인, mock provider 호출 횟수 검증 |
| 회귀 | 기존 `GuideGenerationServiceTest` | 모두 통과 (작은 정책 = SINGLE_CALL 분기) |

## 6. 결정 trade-offs

- **partial 응답을 풀 GuideContent 로 둠** — merge 로직이 복잡해지지만 validator/schema 재사용 가능. 가벼운 키포인트 리스트로 두면 merge LLM 이 "처음부터 가이드 만드는" 호출이 되어 사실상 단일 호출과 다를 바 없어짐.
- **그룹 budget 60K** — 30K 로 더 작게 하면 partial 수 ↑ → 비용 ↑, 60K 면 partial 호출 안전 마진 충분.
- **청크 순서 유지** — PDF 페이지 흐름을 보존해서 partial 가이드의 문맥 일관성 ↑. 단점: ranking 기반 선별이 아니라 정보 우선순위가 청크 순서에 의존.
- **재시도 미포함** — `GuideGenerationEventListener` 에 429 retry 가 들어가면 자연 해결되는 부분이라 본 spec 에서는 다루지 않음.

## 7. 후속 작업 / 미결

- `GuideGenerationEventListener` 에 429 retry + exponential backoff (Resilience4j) — 별도 spec
- OpenAI tier 상향 (TPM 200K → 2M+) — 운영/영업 결정
- `MapReduceGuideOrchestrator` 의 partial 호출 병렬화 — 1차 구현은 순차, rate-limit 안전 후 병렬 가능
- 가이드 품질 모니터링: 큰 정책 (MAP_REDUCE 경로) 결과를 작은 정책 (SINGLE_CALL) 결과와 별도 메트릭으로 추적

## 8. 비용 추적 / 관측성

- partial 호출과 merge 호출 모두 `LlmModule.GUIDE` 로 기록 (기존 `OpenAiChatClient.emitMetric` 흐름 재사용)
- `llm_cost_bucket` 의 `call_count` / `total_tokens` 가 자연스럽게 N+1 배 증가 → 큰 정책 처리량 추적 가능
- 로그 추가: `MapReduceGuideOrchestrator` 진입 시 `log.info("map-reduce 진입: policyId={}, groups={}, total_tokens={}", ...)`

## 9. 마이그레이션 / DB

- DB schema 변경 없음
- 기존 적재된 51개 가이드는 그대로 유효 (재생성 불필요)
- 누락 9개 가이드는 본 구현 배포 후 internal regenerate endpoint (`POST /api/internal/guides/generate`) 로 자연 복구 가능

## 10. Out of Scope (다시 한 번)

- `GuideContent` schema 변경
- `OpenAiChatClient` 의 system prompt 본문 변경 (partial/merge 용 prefix 만 추가)
- RAG 인덱싱 / Q&A 청크 검색 흐름
- 첨부 텍스트 추출 (Apache Tika) 자체 개선
