# 어드민 정책 처리 현황 대시보드 설계

> **상태**: spec (2026-05-29 brainstorming 완료, 정식 spec 으로 승격)
> **선행**: [2026-05-28-policy-enrichment-tracking-design.md](./2026-05-28-policy-enrichment-tracking-design.md) Phase E
> **모듈**: `admin` (백엔드 + 프론트엔드), `policy`, `rag`, `ingestion`
> **관련 이슈**: [#126](https://github.com/TaetaetaE01/youthfit/issues/126)

## 1. 배경 / 동기

2026-05-28 Phase A 가 완료되어 `policy_processing_step` 테이블 + 5단계 추적 인프라가 백엔드에 구축됨 (id 86~90 정책 5건 수동 검증으로 step 기록 정상 확인). 그러나 그 추적 정보를 **운영자가 한눈에 볼 수 있는 어드민 UI** 가 아직 없다.

사용자가 운영하면서 알고 싶은 것은 단순한 "단계별 SUCCESS/FAILED 표" 이상이다. 정책의 **데이터 품질** 까지 한 화면에서 보고 싶음:

1. **모든 정책의 5단계 진행 상황** — 어느 정책이 RAG 까지 갔고, 어느 정책이 GUIDE 에서 막혔는지.
2. **참고 사이트(reference site) 크롤링 여부** — n8n 워크플로우가 enrichment 단계에서 참고사이트 들어가서 풍부한 정보를 가져왔는지 (SPA 라 skip 됐는지, fetch 실패했는지). 정책마다 다름.
3. **첨부파일 임베딩 여부** — 정책의 첨부파일들이 다운로드·텍스트 추출·RAG 임베딩까지 갔는지. 어드민이 부족한 정책은 보강이 필요한지 판단.

본 spec 은 이 3가지 정보를 통합 대시보드로 제공하는 정식 설계다.

## 2. 사용자 요구사항 (raw)

다음은 사용자가 명시한 요구사항. 그대로 보존:

1. "모든 정책들의 5단계 성공했는지 알려주는 어드민 페이지"
2. "추가 사이트도 크롤링했는지 여부"
3. "첨부파일도 확인해서 임베딩했는지에 대한 여부"

→ 핵심: 정책 단위로 "이 정책이 사용자에게 보여줄 만큼 풍부한가" 를 한 화면에서 판단할 수 있어야 함.

## 3. 사용 시나리오 (확정)

대시보드를 사용하는 1차 시나리오:

- **A. 새 적재 직후** — n8n 워크플로우 돌린 직후 "오늘 들어온 N건 모두 RAG 까지 갔나" 확인. 시간 역순 정렬 + 5단계 status 가 핵심.
- **B. 정기 점검** — 주 1회 "전체 N건 중 첨부 임베딩 안 된 정책 / 참조사이트 fetch 실패한 정책" 일괄 보강. 강력한 필터·정렬 + 완성도 컬럼.
- **C. 특정 정책 디버깅** — 사용자 문의나 Q&A 가 이상한 답 줬을 때 특정 정책 ID 입력해서 5단계 + 첨부 + 참조 모두 점검. 정책 검색·ID 진입 빠르게.

**범위 외**: 운영 헬스 KPI 그래프, enrichment job 성공률, 평균 duration 같은 집계 지표는 별도 페이지로 분리 (본 spec 미포함).

## 4. 기존 인프라와의 관계 (재사용)

### 백엔드 (이미 있음)
- `policy_processing_step` 테이블 — Phase A. 5단계 (INGESTION/ENRICHMENT/GUIDE/RULE/RAG_INDEXING) 의 status + reason + duration + attempt.
- `policy_attachment` 테이블 — `extraction_status` (PENDING/DOWNLOADING/DOWNLOADED/EXTRACTED/FAILED/SKIPPED).
- `policy_document` 테이블 — RAG 청크. `source` (BODY/ATTACHMENT/ENRICHMENT_BODY) 로 정책별 첨부 임베딩 여부 판단.
- `enrichment_job` 테이블 — 어드민 강제 enrichment 잡 이력.
- `policy.enrichment` JSONB 컬럼 — sections, cleanedText, extraAttachments, sourceUrl 등.
- `/admin/enrichment/*` 엔드포인트 (`AdminEnrichmentApi`) — 후보 목록, 상세, 참조 사이트 갱신, 강제 잡 생성.

### 프론트엔드 (이미 있음)
- `pages/admin/AdminIngestionPage.tsx` — Ingestion + Enrichment 탭.
- `pages/admin/IngestionHealthTab.tsx` — 수집 헬스 탭.
- `pages/admin/enrichment/EnrichmentCandidateTable.tsx`, `EnrichmentReviewPanel.tsx`, `EnrichmentReferenceSiteEditor.tsx`.

### 본 spec 의 신규 추가
- 백엔드: `/admin/policies/processing/*` endpoint 군 (5종 조회 + 5종 retry).
- 프론트엔드: 신규 페이지 `/admin/policies/processing` + 컴포넌트 6개.
- DB 스키마 변경 **없음**. 기존 테이블만 조회.

## 5. 정보 모델

대시보드 1행 (정책 1건) 에 표시할 신호:

| 신호 | 출처 | 표시 |
|------|------|-----|
| INGESTION status | `policy_processing_step` | dot (●ok ●fail ●skip) |
| ENRICHMENT status | 동일 | dot |
| GUIDE status | 동일 | dot |
| RULE status | 동일 | dot |
| RAG_INDEXING status | 동일 | dot |
| 첨부 다운로드 카운트 | `policy_attachment.extraction_status='DOWNLOADED'/'EXTRACTED'` 집계 | "3/5" (DOWNLOADED+EXTRACTED / 전체) |
| 첨부 텍스트 추출 카운트 | `policy_attachment.extraction_status='EXTRACTED'` 집계 | "2/5" |
| 첨부 임베딩 카운트 | `policy_document` count where `source='ATTACHMENT'` 의 distinct attachment_id | "2/5" |
| 참고 사이트 fetch 결과 | `policy_processing_step.detail_json.skippedUrls` (Phase D 후) | "1/2 SUCCESS" 또는 placeholder |
| 종합 완성도 | derived | 완전 / 부분 / 미흡 |

### 완성도 정의 (확정)
- **완전**: RAG 본문 `SUCCESS` **AND** (첨부 0개 **OR** 모든 첨부 `EXTRACTED` 되고 임베딩 카운트 == 첨부 카운트)
- **부분**: RAG 본문 `SUCCESS` **AND** 첨부 일부 누락 (다운로드 실패 · 추출 실패 · 임베딩 누락 중 하나라도)
- **미흡**: RAG 본문 `FAILED` (또는 아직 RAG_INDEXING step 자체가 없는 경우)

ENRICHMENT 의 참고 사이트 결과는 본 완성도 정의에 포함하지 않음. Phase D 완료 전에는 데이터가 없어 의미가 없기 때문. Phase D 후 별도 컬럼·필터로만 표시.

## 6. 페이지 구조

### 6.1 페이지 위치
- 신규 페이지 `/admin/policies/processing`
- 사이드바 메뉴 항목 "정책 처리 현황" 추가 (기존 `대시보드 / 수집·처리 / RAG 미리보기 / Q&A 캐시 / 비용` 사이)
- `IngestionHealthTab` 본문 상단에 "정책별 처리 현황 보기" 링크 1개 추가 (운영자 동선 보조)

### 6.2 페이지 상단 (KPI + 필터)
**KPI 카드 4개** (좌→우):
- 완전 N건 (전체 대비 %)
- 부분 N건
- 미흡 N건
- 최근 24h 적재 N건 (기준: `policy.created_at` ≥ now - 24h)

**검색·정렬 줄**:
- 텍스트 검색 (정책 ID 또는 제목, 부분 일치)
- 지역 셀렉트 (전체 / 시·도 단위)
- 정렬 셀렉트: 기본 "업데이트 최신순" · 옵션 "완성도 미흡순" · "ID 순"

**빠른 필터 칩 8개** (단일 선택, 클릭 시 활성화, 각 칩에 결과 개수 표시):
1. 전체 (default)
2. 미흡만
3. 부분만
4. RAG 본문 FAILED
5. 첨부 임베딩 누락
6. 참조 fetch 실패 (Phase D 후 활성, 그 전엔 disabled)
7. GUIDE/RULE 실패
8. 최근 24h (KPI 와 동일 기준 — `policy.created_at` ≥ now - 24h)

URL searchParams 로 필터·정렬·검색어·페이지 상태 유지 (북마크 가능, 시나리오 C 의 정책 ID 진입 공유 가능).

### 6.3 정책 표 (1차 시야)
**컬럼 8개**:

| ▸ | ID | 제목 | 완성도 | 5단계 | 첨부 (임베딩/추출/총) | 참조 | 업데이트 |

- **▸**: 클릭 시 펼침
- **완성도**: 뱃지 (완전 녹색 / 부분 황색 / 미흡 빨강)
- **5단계**: dot 5개 (INGESTION → RAG_INDEXING 순). 색: `SUCCESS` 녹색 / `FAILED` 빨강 / `SKIPPED` 회색 / `IN_PROGRESS` 파랑 (pulse 애니메이션) / `PENDING` 또는 step 자체 없음 빈 원. hover 시 단계명 + status tooltip.
- **첨부**: `"{임베딩}/{추출}/{총}"` 형태. 비율 미달이면 색깔 변경 (전부 일치 녹색, 일부 미흡 황색, 0 빨강). 첨부 0건이면 `"—"`.
- **참조**: `"{SUCCESS}/{전체}"`. Phase D 전엔 `"—"` placeholder.
- **업데이트**: `policy.updated_at` 기준 상대 시간 ("10분 전", "2시간 전").

**페이징**: 페이지당 50건 default, 무한 스크롤 또는 페이지네이터 (구현 시 선택). 정책 전체 247건 가정해도 4~5페이지 수준이므로 페이지네이터 충분.

### 6.4 펼침 영역 (행 클릭 시)
펼침 동작: 한 번에 여러 행 동시 펼침 가능 (정책 비교 용도). URL 에 펼침 상태는 저장 안 함 (휘발성).

펼침 영역 레이아웃 (3컬럼 grid):

**(좌) 5단계 처리 이력 테이블**
| 단계 | STATUS | 소요 | 시도 | ⟲ |
- 단계: INGESTION → RAG_INDEXING 5행
- STATUS: SUCCESS/FAILED/SKIPPED + reason (있으면 tooltip)
- 소요: duration_ms → "1.2s"
- 시도: attempt 횟수
- ⟲: 재실행 아이콘 (INGESTION 은 MVP 에서 disabled, n8n trigger 미구현)

**(중) 첨부파일 N건 테이블**
| 파일명 | DL | EXT | EMB | ⟲ |
- 정책의 모든 첨부 N건 표시 (PolicyAttachment 전부)
- DL: extraction_status >= DOWNLOADED 면 ✓
- EXT: extraction_status == EXTRACTED 면 ✓ / FAILED 면 FAIL
- EMB: `policy_document` 의 해당 attachment_id 청크 존재 여부 (✓ 또는 "누락")
- ⟲: 미흡한 행 옆에만 표시 (첨부 1건 재인덱싱)

**(우) 참고 사이트 테이블**
| URL | STATUS | 청크 |
- ENRICHMENT step 의 detail_json.skippedUrls 또는 enrichment.sourceUrls 기반 (Phase D 후 채워짐)
- STATUS: SUCCESS / SPA_DETECTED / TIMEOUT / HTTP_4XX / FETCH_FAILED
- 청크: 해당 URL 에서 생성된 RAG 청크 수
- Phase D 전: `"Phase D 완료 후 채워짐"` 문구만 표시

**하단 통합 액션 줄**:
- `[첨부 임베딩 재인덱싱]` — 정책의 모든 첨부 재실행
- `[RAG 본문 재인덱싱]` — 본문 청크 + 임베딩 다시
- `[전체 재처리]` — 모든 단계 큐잉 (확인 다이얼로그 + 사유 입력)
- `[정책 상세 →]` — 사용자용 정책 상세 페이지 새 탭

## 7. 백엔드 API 명세

### 7.1 조회 endpoint

#### `GET /admin/policies/processing`
정책 처리 현황 목록. 검색·필터·정렬·페이징.

**Query Parameters**:
- `q`: 검색어 (정책 ID 또는 제목 부분 일치, optional)
- `region`: 지역 코드 (optional)
- `filter`: `all` | `incomplete` | `partial` | `rag_failed` | `attachment_embedding_missing` | `reference_fetch_failed` | `guide_rule_failed` | `recent_24h` (default `all`)
- `sort`: `updated_desc` | `completeness_asc` | `id_asc` (default `updated_desc`)
- `page`: 페이지 번호 (default 0)
- `size`: 페이지 크기 (default 50, max 200)

**Response** (`PolicyProcessingListResponse`):
```json
{
  "totalCount": 247,
  "page": 0,
  "size": 50,
  "items": [
    {
      "policyId": 88,
      "title": "청년 도전지원금",
      "region": "서울",
      "completeness": "PARTIAL",
      "stepStatuses": {
        "INGESTION": "SUCCESS",
        "ENRICHMENT": "SUCCESS",
        "GUIDE": "SUCCESS",
        "RULE": "SUCCESS",
        "RAG_INDEXING": "SUCCESS"
      },
      "attachments": { "total": 5, "extracted": 4, "embedded": 2 },
      "references": { "total": 2, "succeeded": 1 },
      "updatedAt": "2026-05-29T03:25:00Z"
    }
  ]
}
```

#### `GET /admin/policies/processing/stats`
KPI 카드 4개용 집계.

**Response** (`PolicyProcessingStatsResponse`):
```json
{
  "totalCount": 247,
  "completeCount": 182,
  "partialCount": 51,
  "incompleteCount": 14,
  "recent24hCount": 8
}
```

#### `GET /admin/policies/{id}/processing`
펼침 영역용 상세.

**Response** (`PolicyProcessingDetailResponse`):
```json
{
  "policyId": 88,
  "title": "청년 도전지원금",
  "completeness": "PARTIAL",
  "steps": [
    { "step": "INGESTION", "status": "SUCCESS", "durationMs": 1200, "attempt": 1, "reason": null, "startedAt": "...", "finishedAt": "..." }
  ],
  "attachments": [
    { "attachmentId": 401, "filename": "신청서.hwp", "extractionStatus": "EXTRACTED", "embedded": true }
  ],
  "references": [
    { "url": "https://seoul.go.kr/youth-rent", "status": "SUCCESS", "chunkCount": 4 }
  ]
}
```

### 7.2 재실행 endpoint

다음 5종 모두 idempotent. 동시 호출 시 마지막 호출만 반영 (낙관적 잠금 또는 `PolicyProcessingStepService` 의 동시성 가정 따름).

| Endpoint | 동작 |
|----------|-----|
| `POST /admin/policies/{id}/processing-steps/{step}/retry` | 단계 1건 재실행. step ∈ {ENRICHMENT, GUIDE, RULE, RAG_INDEXING}. INGESTION 은 400 (n8n trigger 미구현). |
| `POST /admin/policies/{id}/attachments/{attachmentId}/reindex` | 첨부 1건 임베딩 재실행 (`AttachmentReindexService`). |
| `POST /admin/policies/{id}/attachments/reindex` | 정책의 모든 첨부 임베딩 재실행. |
| `POST /admin/policies/{id}/rag/reindex` | 본문 RAG 재인덱싱. |
| `POST /admin/policies/{id}/reprocess` | 전체 재처리 — ENRICHMENT/GUIDE/RULE/RAG 모두 재실행 큐잉. 요청 body 에 `reason` 필드 필수. |

**Response 공통**:
```json
{ "queued": true, "stepIds": [...], "message": "재처리 큐잉됨" }
```

### 7.3 에러 처리
기존 전역 예외 핸들러 + `ErrorCode` 체계 따름.
- 404 `YF-004`: 정책 ID 없음
- 400 `YF-001`: INGESTION 재실행 요청 / 잘못된 step 명
- 409 `YF-005`: 이미 동일 step 이 IN_PROGRESS 인 경우 (재실행 금지)

## 8. 프론트엔드 구조

### 8.1 신규 파일

```
frontend/src/
├── pages/admin/
│   ├── AdminPolicyProcessingPage.tsx           # 라우트 페이지
│   └── policy-processing/
│       ├── PolicyProcessingKpiCards.tsx
│       ├── PolicyProcessingFilters.tsx          # 검색·정렬·칩
│       ├── PolicyProcessingTable.tsx            # 1차 표
│       ├── PolicyProcessingDetailPanel.tsx      # 펼침 영역 (3 표 + 액션)
│       ├── PolicyProcessingRowActions.tsx       # 행별 ⟲ 버튼 군
│       └── ReprocessConfirmDialog.tsx           # 전체 재처리 확인
├── apis/
│   └── adminPolicyProcessing.api.ts
├── hooks/
│   ├── queries/
│   │   ├── useAdminPolicyProcessingList.ts
│   │   ├── useAdminPolicyProcessingDetail.ts
│   │   └── useAdminPolicyProcessingStats.ts
│   └── mutations/
│       ├── useRetryProcessingStep.ts
│       ├── useReindexAttachment.ts
│       ├── useReindexAllAttachments.ts
│       ├── useReindexRag.ts
│       └── useReprocessPolicy.ts
└── types/
    └── adminPolicyProcessing.ts
```

### 8.2 상태 관리
- 서버 데이터: TanStack Query (목록·상세·KPI). retry mutation 성공 시 해당 정책 query invalidate.
- URL 상태: filter/sort/q/region/page (React Router searchParams)
- 펼침 상태: 컴포넌트 로컬 useState (Set<policyId>)

### 8.3 라우팅
`App.tsx` 의 `/admin` 라우트 그룹에 `AdminPolicyProcessingPage` 추가. AdminLayout 사이드바에 메뉴 추가.

## 9. 데이터 흐름

### 9.1 목록 조회 (1차 시야)
```
사용자 진입 → searchParams 읽기 → useAdminPolicyProcessingList(filter, sort, q, region, page)
  → GET /admin/policies/processing?...
    → AdminPolicyProcessingService.findProcessingPolicies()
      → 1) PolicyRepository.findFiltered() — 검색·지역·페이징 (기본 정책 메타)
      → 2) PolicyProcessingStepRepository.findLatestByPolicyIds(ids) — 5단계 status 일괄 조회
      → 3) PolicyAttachmentRepository.aggregateByPolicyIds(ids) — 첨부 카운트 집계
      → 4) PolicyDocumentRepository.countAttachmentEmbeddingsByPolicyIds(ids) — 임베딩 카운트
      → 5) (Phase D 후) ENRICHMENT step.detail_json 에서 참조 사이트 결과 집계
      → 6) 완성도 derived 계산 (서비스 레이어)
      → 7) filter 조건 (예: incomplete) 은 step + 첨부 데이터 결합 후 적용
    → PolicyProcessingListResponse 조립
```

성능: 한 번에 정책 50건 + 5단계 + 첨부 집계. N+1 회피 — Repository 메서드는 모두 `IN (?)` 일괄 조회.

### 9.2 상세 조회 (펼침)
```
행 클릭 → useAdminPolicyProcessingDetail(policyId)
  → GET /admin/policies/{id}/processing
    → 1) PolicyProcessingStepRepository.findAllByPolicyId(id) — 5단계 전체 + 최신 attempt
    → 2) PolicyAttachmentRepository.findByPolicyId(id)
    → 3) PolicyDocumentRepository.findEmbeddedAttachmentIds(id)
    → 4) PolicyProcessingStepRepository.findEnrichmentStep(id).detail_json.skippedUrls
    → PolicyProcessingDetailResponse 조립
```

### 9.3 재실행
```
[RAG 본문 재인덱싱] 클릭
  → useReindexRag mutation
  → POST /admin/policies/{id}/rag/reindex
    → AdminPolicyProcessingService.reindexRag(id)
      → RagIndexingService.requestIndexing(policy) — 기존 서비스 재사용
      → PolicyProcessingStepService.markStarted(policyId, RAG_INDEXING)
    → 응답 즉시 반환 (비동기 처리)
  → mutation onSuccess → invalidate 해당 policyId query
  → 표 자동 refetch → 새 status 반영
```

[전체 재처리] 는 확인 다이얼로그 + reason 입력 필수. ENRICHMENT/GUIDE/RULE/RAG 4단계 큐잉.

## 10. 진행 순서 (Phase 와의 관계)

본 spec 은 원래 plan 의 Phase E 지만, **즉시 진행 (A → E → B → C → D)** 으로 변경.

| Phase | 상태 | 영향 |
|-------|-----|------|
| A | 완료 | `policy_processing_step` 테이블 + 5단계 추적 — 본 spec 의 데이터 소스 |
| **E (본 spec)** | **다음** | 대시보드 즉시 구축. 참조 컬럼은 Phase D 후 채워짐 placeholder |
| B | 대기 | `policy.processing_status` 컬럼 — 대시보드는 `policy_processing_step` 직접 조회로 동작 가능. B 완료 후 완성도 판단을 컬럼 1개 조회로 단순화 가능 (refactor) |
| C | 대기 | 사용자 페이지 — 본 spec 과 무관 |
| D | 대기 | n8n 워크플로우 개편 — D 완료 시 참조 사이트 데이터 채워짐. 대시보드 UI 는 변경 없음 (placeholder → 실제 데이터 자동 전환) |

**Phase D 와의 약한 결합**:
- 본 spec 의 `references` 응답 필드 스키마는 Phase D 가 채울 형식과 일치하도록 미리 정의.
- Phase D 의 `policy_processing_step.detail_json.skippedUrls` 구조는 본 spec 의 응답 스키마에 1:1 매핑되어야 함 — Phase D 진행 시 본 spec 의 7.1 응답 형식을 따라야 한다는 제약을 Phase D plan 에 명시.

## 11. 비기능 요구사항

### 성능
- 1차 목록 조회: 정책 50건 + 5단계 + 첨부 + 임베딩 집계 < 500ms (p95).
- 펼침 상세 조회: < 200ms (p95).
- N+1 회피: Repository 메서드는 정책 ID 리스트 단위 일괄 조회.

### 보안
- 모든 endpoint 는 `ADMIN` 권한 필수 (기존 admin 미들웨어 재사용).
- 재실행 endpoint 는 audit log 기록 (`enrichment_job` 패턴 따라 새 `policy_admin_action` 테이블 또는 기존 로그 인프라 재사용 — plan 단계에서 결정).

### 페이징·정렬
- 페이지당 50건 default, max 200.
- 정렬 옵션 3가지 — `updated_desc` 외 `completeness_asc`/`id_asc` 는 SQL ORDER BY 로 처리. completeness 는 enum 정렬을 위해 case-when 필요.

## 12. 테스트 전략

### 백엔드
- **AdminPolicyProcessingServiceTest** — 완성도 계산 로직 단위 테스트 (첨부 0건·일부 누락·전부 임베딩 케이스).
- **AdminPolicyProcessingControllerTest** (MockMvc) — 7종 endpoint 각각 200/400/404/409 응답 검증.
- **PolicyProcessingStepRepositoryTest** — `findLatestByPolicyIds` 동시성·정렬·N+1 회피 검증 (TestContainers Postgres).
- **재실행 통합 테스트** — `POST /retry` → `RagIndexingService` 호출 → step 기록 갱신까지 한 흐름.

### 프론트엔드
- **PolicyProcessingTable.test.tsx** — 1차 표 렌더링, 완성도 뱃지 색깔, 5단계 dots, 첨부/참조 카운트 색깔.
- **PolicyProcessingDetailPanel.test.tsx** — 펼침 영역 표 3개 + 액션 버튼 + 재실행 mutation 호출.
- **PolicyProcessingFilters.test.tsx** — 칩 클릭 시 URL searchParams 갱신, 검색·정렬·지역 셀렉트.
- **useAdminPolicyProcessingList.test.ts** — mutation 후 invalidate 동작.

## 13. 성공 기준

1. 운영자가 `/admin/policies/processing` 진입 시 1차 표 정책 50건이 500ms 내 로드된다.
2. 한 정책 행 클릭 시 펼침 영역 3개 표 + 액션 버튼이 200ms 내 로드된다.
3. "미흡만" 칩 클릭 시 RAG 본문 FAILED 인 정책만 필터링되어 표시된다.
4. "첨부 임베딩 누락" 칩 클릭 시 첨부가 EXTRACTED 되었지만 `policy_document` 에 청크가 없는 정책이 필터링된다.
5. 펼침의 [첨부 임베딩 재인덱싱] 클릭 시 해당 정책의 모든 첨부에 대해 `AttachmentReindexService` 가 호출되고, 5초 내 표가 갱신되어 새 status 가 보인다.
6. [전체 재처리] 클릭 시 확인 다이얼로그가 뜨고, reason 입력 후 큐잉되며, `policy_processing_step` 에 새 attempt 가 기록된다.
7. Phase D 완료 후 별도 코드 변경 없이 참조 사이트 컬럼이 실제 데이터로 채워진다 (스키마 1:1 매핑 가정).

## 14. 다음 단계

1. 본 spec 을 plan 으로 — phase 분할 (백엔드 API → 프론트엔드 UI → 재실행 액션).
2. plan 실행 → PR 단위 머지.
3. 운영 검증 (정책 86~90 기준 5단계 표시 정상).
