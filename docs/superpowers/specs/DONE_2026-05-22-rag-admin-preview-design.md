# RAG Admin Preview — Hybrid Search Tuning Tool

- 작성일: 2026-05-22
- 상태: Design (브레인스토밍 승인 완료, 구현 계획 단계 대기)
- 작성자: brainstorming with user
- 관련 모듈: `backend/admin`, `backend/rag`, `frontend/admin`

---

## 1. 배경

현재 YouthFit 의 RAG 하이브리드 검색은 다음 정적 설정에 의존한다.

- `HybridSearchProperties` (`rag.hybrid.*`): `enabled`, `topNPerSearch`, `rrfK`, `trigramThreshold`
- `KeywordBoostProperties` (`youthfit.rag.keyword-boost.*`): `enabled`, `maxKeywords`, `stopwords`

값을 바꾸려면 `application.yml` 수정 + 재배포가 필요하고, 변경 효과를 사람이 검증할 도구가 없다. 결과적으로 RRF 가중치/임계치 튜닝 사이클이 길고 직관에 의존한다.

## 2. 목표

**튜닝 사이클 단축**: 어드민이 한 화면에서 정책+쿼리를 입력하고, 운영 yml 기준(baseline) vs 후보 설정(candidate) 의 top-k 검색 결과를 즉시 비교할 수 있게 한다. LLM 답변 생성은 하지 않는다.

### Non-goals (V1 의도적 제외)

자세한 목록은 §10 참조. 핵심:
- 어드민에서 변경한 값을 운영에 hot-apply 하지 않음 (적용은 PR + 재배포)
- Golden Q&A 회귀 평가 안 함 (별도 스코프)
- LLM 답변 A/B 생성 안 함
- 결과 공유/세션 기록/즐겨찾기 안 함
- 모바일 대응 안 함

## 3. 핵심 제약

- **drift 금지**: candidate 결과는 같은 입력으로 운영 yml 을 그 값으로 바꿔 재배포했을 때와 정확히 동일해야 한다. → 운영과 어드민이 **같은 코드 경로**(`RagSearchService`) 를 타도록 강제.
- **read-only**: 어드민이 무엇을 입력해도 운영 설정 / DB 상태 / 캐시 / 메트릭은 바뀌지 않는다.
- **LLM 비용 가드**: embedding 1회만 호출, Chat API 호출 없음. embedding 도 한 요청 안에서 baseline/candidate 가 공유.
- **hybrid 비활성 경로 일관성**: baseline 또는 candidate 중 어느 한쪽의 `hybridEnabled=false` 이면 해당 쪽 응답에서 `trigramTopN` 은 빈 리스트, `merged` 는 vector 결과만 포함 (운영 코드 분기 그대로 노출). UI 는 이 경우 Trigram 탭에 "비활성" 안내 표시.

## 4. API

### Endpoint

```
POST /api/admin/rag/preview
Authorization: Bearer <admin-jwt>
Content-Type: application/json
```

### Request

```jsonc
{
  "policyId": 12345,
  "query": "주거 지원 자격이 어떻게 되나요",
  "candidate": {
    "hybridEnabled": true,
    "topNPerSearch": 30,
    "rrfK": 30,
    "trigramThreshold": 0.15,
    "keywordBoostEnabled": true,
    "maxKeywords": 7
  }
}
```

규칙:
- `candidate` 의 모든 필드는 **optional**. 미지정 필드는 baseline 값을 그대로 사용 → "이 한 가지만 바꿔봤을 때" 좁은 비교 가능.
- baseline 은 클라이언트가 보내지 않는다. 서버가 현재 `HybridSearchProperties` + `KeywordBoostProperties` 에서 자동 주입.

### Response

```jsonc
{
  "policyId": 12345,
  "query": "주거 지원 자격이 어떻게 되나요",
  "extractedKeywords": ["주거", "지원", "자격"],
  "baseline": {
    "config": { /* 실제 사용된 effective config */ },
    "vectorTopN":  [ { "chunkId": 7, "chunkIndex": 3, "distance": 0.21, "preview": "..." } ],
    "trigramTopN": [ /* 동일 */ ],
    "merged":      [ { "chunkId": 7, "rrfScore": 0.0312, "rank": 1, "preview": "..." } ],
    "tookMs": 142
  },
  "candidate": { /* 동일 구조 */ },
  "diff": {
    "rankChanges": [
      { "chunkId": 7,  "baselineRank": 1,    "candidateRank": 3,    "delta": -2 },
      { "chunkId": 22, "baselineRank": null, "candidateRank": 1,    "delta": "NEW" },
      { "chunkId": 5,  "baselineRank": 4,    "candidateRank": null, "delta": "DROPPED" }
    ]
  }
}
```

규칙:
- `diff.rankChanges` 는 **merged 결과 기준**으로 서버가 계산해 보낸다. (프론트는 단순 렌더링만.)
- `preview` 는 chunk 당 최대 500자 truncate. 페이로드 상한은 약 30KB.

### 검증/에러

| 케이스 | 상태 |
|---|---|
| `query` blank 또는 500자 초과 | 400 |
| `policyId` 음수/0 | 400 |
| `candidate.topNPerSearch` 범위 외 (1~100) | 400 |
| `candidate.rrfK` 범위 외 (1~500) | 400 |
| `candidate.trigramThreshold` 범위 외 (0.0~1.0) | 400 |
| `candidate.maxKeywords` 범위 외 (0~20) | 400 |
| 존재하지 않는 policyId | 404 |
| 인증 없음 | 401 |
| 비-admin JWT | 403 |
| 분당 30회 초과 | 429 + `Retry-After` |
| embedding API 실패 | 502 + 상세 메시지 (어드민 도구이므로 그대로 노출) |

## 5. 도메인 · application 변경

### 신규 (admin 모듈)

```
backend/src/main/java/com/youthfit/admin/rag/
├── presentation/
│   ├── controller/
│   │   ├── AdminRagPreviewApi.java          # Swagger interface (@Tag, @Operation)
│   │   └── AdminRagPreviewController.java   # POST /api/admin/rag/preview
│   └── dto/
│       ├── request/
│       │   ├── RagPreviewRequest.java
│       │   └── HybridOverrideRequest.java
│       └── response/
│           ├── RagPreviewResponse.java
│           ├── PreviewSideResponse.java     # baseline/candidate 공통
│           ├── ChunkSummaryResponse.java
│           └── RankChangeResponse.java
└── application/
    ├── service/
    │   └── RagPreviewService.java
    └── dto/
        ├── command/
        │   ├── RagPreviewCommand.java
        │   └── HybridOverrideCommand.java   # 모든 필드 nullable
        └── result/
            ├── RagPreviewResult.java
            ├── PreviewSideResult.java
            └── RankChangeResult.java
```

### 수정 (rag 모듈)

#### 5.1 `RagSearchService` — 신규 trace 오버로드

기존 시그니처는 그대로 유지(운영 호출자 영향 0).

```java
// 기존 (변경 없음)
public List<PolicyDocumentChunkResult> searchRelevantChunks(SearchChunksCommand cmd);
public List<PolicyDocumentChunkResult> searchRelevantChunks(
        SearchChunksCommand cmd, float[] precomputedEmbedding);

// 신규 (어드민 전용)
public RagSearchTrace searchRelevantChunksWithTrace(
        SearchChunksCommand cmd,
        float[] precomputedEmbedding,
        @Nullable HybridSearchOverrides overrides
);
```

- `overrides == null` 이면 baseline (현재 yml) 으로 실행.
- `overrides != null` 이면 EffectiveConfig 가 baseline 위에 부분 override 적용.
- 내부의 `hybridSearch(...)` 를 살짝 분해해 `EffectiveConfig` 를 인자로 받도록 한다. 운영 호출은 `EffectiveConfig.from(properties, keywordBoostProperties)` 로 변환해 동일 메서드 호출.

#### 5.2 신규 record (rag.application.dto)

```java
// command
public record HybridSearchOverrides(
    Boolean hybridEnabled,
    Integer topNPerSearch,
    Integer rrfK,
    Double trigramThreshold,
    Boolean keywordBoostEnabled,
    Integer maxKeywords
) {}

// result
public record RagSearchTrace(
    EffectiveConfig effective,
    List<SimilarChunk> vectorTopN,
    List<SimilarChunk> trigramTopN,
    List<MergedChunk> merged,
    List<String> usedKeywords,
    long tookMs
) {}

public record EffectiveConfig(
    boolean hybridEnabled,
    int topNPerSearch,
    int rrfK,
    double trigramThreshold,
    boolean keywordBoostEnabled,
    int maxKeywords
) {
    public static EffectiveConfig from(HybridSearchProperties h, KeywordBoostProperties k);
    public static EffectiveConfig from(HybridSearchProperties h, KeywordBoostProperties k,
                                       HybridSearchOverrides overrides);
}

public record MergedChunk(
    long chunkId, int chunkIndex, double distance, double rrfScore, int rank, String preview
) {}
// distance: ReciprocalRankFusion 이 유지한 SimilarChunk.distance 를 그대로 보존 (vector 우선)
```

#### 5.3 `RagPreviewService` 흐름

```
embed(query) 1회                                  // EmbeddingProvider
└─ baseline:   RagSearchService.searchRelevantChunksWithTrace(cmd, emb, null)
   candidate:  RagSearchService.searchRelevantChunksWithTrace(cmd, emb, overrides)
   (V1 은 순차 실행 — 단순함 우선)
└─ RankChangeCalculator.compute(baseline.merged, candidate.merged)
└─ map → RagPreviewResult
```

- `@Transactional(readOnly=true)` 한 번만.
- baseline / candidate 두 호출은 같은 트랜잭션·같은 스레드에서 순차 실행.

### 변경하지 않는 것

- `HybridSearchProperties`, `KeywordBoostProperties` (운영 단일 진실).
- `ReciprocalRankFusion` 알고리즘 (`rrfK`, `topK` 가 이미 파라미터).
- `PolicyDocumentRepository` 인터페이스/구현체.

### 의존 방향

```
admin/rag (presentation, application)
        ↓ uses
rag.application.service.RagSearchService     (시그니처 1개 추가)
rag.application.dto.result.RagSearchTrace    (신규)
rag.application.dto.result.EffectiveConfig   (신규)
rag.application.dto.command.HybridSearchOverrides  (신규)
```

위반 없음 (admin → rag, presentation → application → domain).

## 6. 프론트엔드 UI

### 라우트

- `/admin/rag-preview` (신규)
- `RequireAdmin` 가드 + admin 사이드바에 항목 추가
- **모바일 미대응**: `md` 이하에서는 "데스크톱 화면에서 사용해주세요" 안내

### 화면 구조

```
┌────────────────────────────────────────────────────────────────┐
│  RAG 검색 미리보기                                              │
├────────────────────────────────────────────────────────────────┤
│  [정책 ID] [정책 검색 셀렉터 ▾]                                  │
│  [쿼리: ___________________]   [▶ 비교 실행]                    │
├────────────────────────────────────────────────────────────────┤
│  추출 키워드: [주거] [지원] [자격]                              │
├──────────────────────────────┬─────────────────────────────────┤
│  Baseline (yml)      142 ms  │  Candidate            167 ms    │
│  (read-only config 표시)     │  (편집 가능 config 폼)          │
│                              │                                 │
│  [Merged][Vector][Trigram]   │  [Merged][Vector][Trigram]      │
│  1. chunk#7   d=0.21  rrf=…│  1. chunk#22  rrf=…  [NEW]      │
│  2. chunk#13  d=0.24  rrf=…│  2. chunk#7         [↓1]        │
│  3. chunk#22  d=0.27  rrf=…│  3. chunk#13        [↓1]        │
│  ...                         │  ...                            │
└────────────────────────────────────────────────────────────────┘
```

### 컴포넌트 분할

```
frontend/src/components/admin/rag-preview/
├── RagPreviewControls.tsx          # 정책/쿼리 입력 + 실행 버튼
├── CandidateConfigForm.tsx         # 우측 편집 폼 (RHF + Zod)
├── BaselineConfigPanel.tsx         # 좌측 read-only
├── ResultTabs.tsx                  # [Merged][Vector][Trigram]
├── ChunkRow.tsx                    # rank, chunkId, distance, rrfScore, preview
└── RankDeltaBadge.tsx              # ↑3 / ↓2 / NEW / DROPPED

frontend/src/apis/adminRag.api.ts        # ragPreview(req)
frontend/src/hooks/mutations/useRagPreview.ts   # useMutation
frontend/src/pages/admin/AdminRagPreviewPage.tsx
```

### 데이터 흐름

- **useMutation** 사용 (auto-fetch 금지 — 사용자가 "비교 실행" 누를 때만 호출, embedding 비용 보호).
- `CandidateConfigForm` 초기값은 첫 응답의 `baseline.config` 로 prefill.
- chunk preview 는 기본 120자 truncate + 클릭 expand. 추출 키워드 하이라이트.

### UX 디테일

- 정책 선택: ID 직접 입력(필수) + 타이틀 자동완성(편의, 기존 `apis/policy.api.ts` 재사용).
- 실행 중: 양쪽 패널 스켈레톤. 응답 성공 시 동시 fade-in.
- 에러: 페이지 상단 alert + 직전 결과 유지(재시도 편의).
- URL state 저장 안 함 (공유 수요 약함).

## 7. 권한 · 감사 · 보호장치

### 권한
- 컨트롤러 `@PreAuthorize("hasRole('ADMIN')")`.
- 프론트 `RequireAdmin` 가드 + 사이드바 권한별 표시.

### Rate limit
- **분당 30회 / admin**. Redis sliding window. 키: `rate-limit:admin-rag-preview:{userId}`.
- 초과 시 429 + `Retry-After`.

### Input 가드
- §4 의 검증 규칙을 컨트롤러 진입 시점 `@Valid` + 커스텀 validator 로 강제.

### 감사 로그
- 구조화 로그 1줄만 (별도 audit 테이블 없음).
- 형식:
  ```
  INFO admin.rag.preview userId=42 policyId=12345 query="..."
       baseline={rrfK:60,topN:20,trigramTh:0.10,kwBoost:true,maxKw:5}
       candidate={rrfK:30,topN:30,trigramTh:0.15,kwBoost:true,maxKw:7}
       baselineMs=142 candidateMs=167 baselineHits=10 candidateHits=10
       rankChanges=4
  ```
- `query` 는 앞 200자만 로깅.

### LLM 비용 보호
- embedding 1회만 사용 (Chat API 호출 없음).
- 비용은 `metrics` 모듈에 `source=admin-rag-preview` 태그로 기록 → 어드민 LLM 비용 페이지에서 분리 가시화.

### 페이로드 상한
- `preview` 필드 chunk 당 500자 truncate.
- 한 응답당 chunk 최대 60개 → 약 30KB.

## 8. 테스트

### 백엔드 — 단위
- `EffectiveConfig`: baseline-only / partial override / null override 케이스
- `RankChangeCalculator`: 동일/NEW/DROPPED/순위 변동/빈 결과

### 백엔드 — application 서비스
- `RagSearchServiceTest` 확장:
  - `searchRelevantChunksWithTrace(cmd, emb, null)` → 운영 결과와 동일
  - overrides 적용 시 해당 필드만 반영
  - `hybridEnabled=false` override → vector-only 경로
  - `keywordBoostEnabled=false` override → keywords 빈 리스트로 호출
  - trigram repository 예외 시 fallback 유지
- `RagPreviewServiceTest` (신규):
  - embedding 1회만 호출 (`verify(..., times(1))`)
  - baseline / candidate trace 둘 다 반환
  - `diff.rankChanges` 계산 정확
  - `tookMs > 0`

### 백엔드 — 컨트롤러 슬라이스 (`@WebMvcTest`)
- 정상(admin) → 200 + 응답 스키마
- 비-admin → 403 / 인증 없음 → 401
- 검증 위반(query blank, policyId 음수, rrfK=0, topN=1000 등) → 400 + field 명시
- 분당 31번째 → 429 + `Retry-After`
- 존재하지 않는 policyId → 404

### 백엔드 — 통합 (`@SpringBootTest`, **1개만**)
- testcontainers Postgres(pgvector) + Redis
- 정책 1개 + chunk 5개 시드 → baseline vs candidate(rrfK=30) → 두 결과 + diff 검증
- 목적: 컨트롤러 → 서비스 → repository → pgvector wire 확인

### 프론트엔드
- `CandidateConfigForm`: baseline prefill, 음수 입력 폼 에러, onChange 가 변경된 필드만 포함
- `RankDeltaBadge`: ↑/↓/NEW/DROPPED/변화 없음
- `ResultTabs`: 탭 전환, empty state
- `useRagPreview`: 성공/실패/auto-fetch 없음
- 페이지 통합: MSW 로 정상/500/권한 차단 시나리오

### 커버리지 목표
- 백엔드 신규: line 80%+ (domain 100%, application 90%+, presentation 70%+)
- 프론트 신규: 핵심 path 커버, snapshot 의존 지양

### 의도적으로 안 함
- E2E (Playwright)
- rate limit 의 실제 시간 경과 테스트 (Redis 카운터 단위 테스트로 대체)
- 성능 벤치마크

## 9. 성공 기준

| # | 기준 |
|---|---|
| 1 | 어드민이 정책 1개 + 쿼리 + candidate 설정 입력 후 baseline/candidate 비교 결과를 **30초 안에** 본다 |
| 2 | candidate 결과는 같은 입력으로 운영 yml 을 그 값으로 바꿔 재배포했을 때와 **동일**하다 (drift 0) |
| 3 | 비교 결과에서 어떤 chunk 가 새로 들어왔고/빠졌고/순위 바뀌었는지 한눈에 보인다 |
| 4 | 분당 30회 초과 호출 시 429 로 거부된다 |
| 5 | 비-admin 토큰으로 접근 시 403 |
| 6 | 백엔드 신규 코드 라인 커버리지 80%+ |

## 10. Out of Scope (V1 의도적 제외)

| 항목 | 안 하는 이유 | 언제 다시 볼지 |
|---|---|---|
| 운영 hot-apply | "도구 → 운영 사고" 경로 금지. PR + 재배포 유지. | audit/rollback 인프라가 갖춰진 후 |
| 3+ candidate 비교 | YAGNI. 2개 비교로 RRF 튜닝 충분. | 3개 이상 요구가 실제로 반복될 때 |
| Golden Q&A 회귀 평가 | 별도 스코프. 도구 목적이 다름. | 튜닝 사이클 1~2회 돈 뒤 회귀 안전망 필요성이 명확해질 때 |
| LLM 답변 A/B 생성 | 비용/속도. 검색 순위 비교로 충분. | 순위는 같은데 답변 품질이 다른 케이스 반복 관찰 시 |
| 세션 기록 / 즐겨찾기 / 공유 링크 | 어드민 1~2명 도구, 협업 수요 약함. | 어드민 인원 증가 또는 정기 회의 안건화 시 |
| 별도 audit 테이블 | 구조화 로그로 충분. | 컴플라이언스 감사 요구 발생 시 |
| embedding 캐시 | drift 위험 (도구 신뢰성 우선). | 사실상 영구 미도입 (캐시 무결성 보장 불가) |
| 모바일 대응 | 좌우 분할 UX 가 모바일에 무의미. | 영구 |
| URL state 공유 | 위 "공유 링크" 와 동일 이유. | 협업 수요 발생 시 |
| `PolicyDocumentRepository` 인터페이스 변경 | 기존 메서드로 충분. | 새 검색 알고리즘(BM25 등) 추가 시 |
| 검색 알고리즘 자체 변경 | 본 스펙은 "기존 알고리즘의 파라미터 튜닝". | 별도 RAG 고도화 스프린트 |

## 11. 후속 작업 (참고)

1. **PolicyDetailPage 분할** — 본 브레인스토밍에서 분리된 다른 주제
2. **Strategy 객체로 추출** (§5 의 옵션 B) — 운영/어드민 분기가 코드에 보이는 게 거슬릴 때
3. **Golden Q&A 회귀 평가 CI** — 더 큰 작업
4. **운영 설정 hot-apply** — audit + rollback 인프라 선행 필요
