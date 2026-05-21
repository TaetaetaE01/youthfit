# Admin Enrichment Review — Design Spec

- **작성일**: 2026-05-21
- **주제**: 어드민 페이지에서 enrichment 빈약·실패 정책을 식별하고, 어드민이 확인된 reference URL 을 입력하면 n8n 워크플로우를 통해 재크롤·재 enrich 하는 운영 워크플로우
- **상태**: Draft (브레인스토밍 결과)

---

## 1. 배경 및 목표

YouthFit 은 온통청년(youth-center) 정책을 n8n + enrichment-merge mega-node 로 자동 수집·강화한다 (PR #111, b53aacc). 자동 enrichment 는 다음과 같은 사유로 빈약하거나 실패할 수 있다.

- 정책 페이지가 첨부 PDF 위주여서 본문 추출 불가
- reference URL 이 누락되거나 잘못됨
- 외부 사이트 응답 실패 / 본문 너무 짧음 / LLM confidence 미달

현재는 어드민이 이런 정책을 식별하거나 수동으로 개선할 수 있는 표면이 없다. `AdminIngestionPage` 가 ingestion 실패 재시도만 지원한다.

**목표**: 어드민(개발자)이 다음을 할 수 있어야 한다.

1. 빈약·실패 enrichment 후보 정책을 한 화면에서 골라낸다.
2. 정책의 reference URL 을 직접 확인·수정·추가한다.
3. 입력한 URL 로 n8n force-enrich 워크플로우를 호출해 재크롤·재 enrich 한다.
4. 잡 진행 상태(PENDING/RUNNING/SUCCESS/FAILED)와 결과를 화면에서 추적한다.

**범위 외 (YAGNI)**

- 멀티 인스턴스 분산 락 (ShedLock)
- 어드민 알림(이메일·슬랙)
- 잡 우선순위/큐
- "검토 완료" 명시 표시 (needsReview 자동 갱신으로 충분)
- 어드민 사용자별 활동 통계

---

## 2. 핵심 결정 사항

| 결정 | 선택 | 이유 |
|---|---|---|
| 빈약 판정 | **자동 + 어드민 필터** | 일관성과 운영 효율. 자동 후보 + 수동 검색 모두 지원 |
| 재크롤 경로 | **n8n 워크플로우 재사용** | enrichment-merge 로직 단일 진실(SoT) 유지 |
| URL 저장 | **`referenceSites` 영구 저장 + ADMIN 플래그** | 다음 자동 enrich 에서도 검증된 URL 우선 사용 |
| 실행 모델 | **비동기 + 상태 폴링** | 분 단위 작업 + 다건 동시 실행 가능 |
| UI 위치 | **`AdminIngestionPage` 내 신규 탭** | 운영 도구 단일화. 사이드바·라우팅 변경 최소 |
| 아키텍처 | **잡 테이블 + on-the-fly 판정 (Option A)** | 폴링·감사·동시성 단일 테이블로 해결. 판정 기준 변경 시 마이그레이션 불필요 |

---

## 3. 데이터 모델

### 3.1 `EnrichmentJob` (신규 테이블)

```sql
CREATE TABLE enrichment_job (
  id              BIGSERIAL PRIMARY KEY,
  policy_id       BIGINT NOT NULL REFERENCES policy(id),
  requested_by    VARCHAR(64) NOT NULL,   -- 어드민 식별자 (이메일 또는 user id)
  requested_urls  JSONB NOT NULL,         -- 이번 잡에서 사용할 ref URL 목록
  status          VARCHAR(16) NOT NULL,   -- PENDING / RUNNING / SUCCESS / FAILED
  attempt         INT NOT NULL,           -- 동일 정책에 대한 누적 시도 (감사용)
  error_message   TEXT,
  requested_at    TIMESTAMP NOT NULL,
  started_at      TIMESTAMP,
  finished_at     TIMESTAMP
);

-- 정책당 진행 중 잡 최대 1개
CREATE UNIQUE INDEX ix_enrichment_job_one_active
  ON enrichment_job (policy_id)
  WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX ix_enrichment_job_policy_recent
  ON enrichment_job (policy_id, requested_at DESC);
```

- 폴링 시 어드민 화면은 정책별 **가장 최근 잡 1건**만 노출.
- partial unique index 로 동시 실행 방지.
- `attempt` 값은 잡 생성 시점에 `SELECT max(attempt)+1 FROM enrichment_job WHERE policy_id = ?` 로 계산 (없으면 1). 같은 트랜잭션 내에서 unique index 가 동시성 충돌을 잡아 줌.

### 3.2 `PolicyReferenceSite` 확장

기존:
```java
record PolicyReferenceSite(String name, String url) { }
```

변경:
```java
record PolicyReferenceSite(String name, String url, Source source) { }

enum Source { AUTO, ADMIN }
```

- 기존 JSONB 데이터는 마이그레이션 SQL 로 `"source":"AUTO"` 백필.
- enrichment 실행 시 URL 선택 우선순위: **ADMIN > AUTO**.
- 어드민이 같은 URL 을 다시 넣으면 `AUTO → ADMIN` 으로 승격(중복 추가 X).

### 3.3 `needsReview` 판정 규칙 (on-the-fly)

다음 중 **하나라도 참**이면 `needsReview = true`:

| 조건 | 의미 |
|---|---|
| `enrichment == null` | 한 번도 enrich 되지 않음 |
| `enrichment.status != OK` | NO_LINK / FETCH_FAILED / TOO_SHORT / LLM_FAILED / PARSE_FAILED / LOW_CONFIDENCE |
| `enrichment.confidence < 0.6` | 임계값 미달 |
| `detailLevel == LITE` | 컨텐츠 깊이 부족 |
| `sections` 핵심 3개 (`supportTarget`, `supportContent`, `selectionCriteria`) 중 2개 이상 결측 | 빈약 |

- 목록 API 에서 SQL `WHERE` 절로 표현 (JSONB 함수).
- 별도 `needs_review` 컬럼 미도입 (판정 기준 변경 시 마이그레이션 불필요). KPI 가시화가 필요해질 때 컬럼 materialize 는 후속 작업.

---

## 4. 백엔드 API

모두 `/api/v1/admin/enrichment/*` 경로, ADMIN role 필요.

| Method | Path | 용도 |
|---|---|---|
| `GET` | `/candidates` | 검토 후보 목록. 쿼리: `needsReview`(bool), `status`(콤마), `detailLevel`, `q`(title LIKE), `page`, `size`, `sort` |
| `GET` | `/candidates/summary` | 카드용 집계 — `total`, `needsReview`, `byStatus{}`, `byDetailLevel{}` |
| `GET` | `/policies/{policyId}` | 어드민 검토 패널용 상세 — Policy 핵심 필드 + 전체 enrichment(sections raw, confidence, fetchedAt, extractor) + referenceSites(`source` 포함) + 최근 잡 5건 |
| `PUT` | `/policies/{policyId}/reference-sites` | 어드민이 URL 목록 저장. body: `[{name, url, source:"ADMIN"}]`. 전체 교체 의미이며 기존 AUTO 는 백엔드가 머지 보존 |
| `POST` | `/policies/{policyId}/jobs` | 재크롤 잡 생성. body: `{ urls?: string[] }` (생략 시 현재 `referenceSites` 사용). 응답: `{jobId, status:"PENDING"}` 202. 이미 PENDING/RUNNING 잡 있으면 409 |
| `GET` | `/jobs/{jobId}` | 단일 잡 폴링 (선택, 보통 `/candidates` 에 latestJob 동봉) |
| `POST` | `/jobs/{jobId}/callback` | **n8n → 백엔드 콜백 전용**. body: `{status: "RUNNING"\|"SUCCESS"\|"FAILED", error?: string}`. 시크릿 헤더(`X-Ingestion-Token`) 검증 |

콜백을 별도 엔드포인트로 둔 이유: 기존 `/api/v1/ingestion/...` 는 enrichment 결과를 받는 표준 경로라 그대로 둬야 함. 잡 라이프사이클 마무리는 별도 신호가 필요함.

---

## 5. 실행 흐름

```
[Frontend]                [Backend]                 [n8n]
   │ POST /jobs            │                          │
   │ ───────────────────▶  │ (1) 동시 잡 체크         │
   │                       │ (2) EnrichmentJob 생성   │
   │                       │     status=PENDING       │
   │                       │ (3) POST force-enrich    │
   │                       │     webhook              │
   │                       │ ──────────────────────▶  │
   │  202 {jobId,PENDING}  │                          │
   │ ◀───────────────────  │                          │
   │                       │ ◀──── PATCH RUNNING ───  │ (4)
   │ GET /candidates ─────▶ │  RUNNING                │
   │                       │                          │ (5) 멀티 URL 크롤·머지·LLM
   │                       │ ◀──── 기존 ingestion ── │ (6)
   │                       │       (enrichment 갱신) │
   │                       │ ◀──── callback SUCCESS ─│ (7)
   │ GET /candidates ─────▶ │  latestJob=SUCCESS      │
```

### 5.1 n8n 워크플로우 (force-enrich)

신규 webhook 워크플로우 1개:

1. POST 수신 → `{jobId, policyId, urls}`
2. 백엔드 `/jobs/{jobId}/callback` 로 `RUNNING` PATCH
3. 기존 `enrichment-merge` mega-node 재호출 (`urls` 인자로 override). PR #111 의 `selectUrls/mergeFetchResults` 그대로 사용
4. 결과를 기존 ingestion 수신 엔드포인트로 POST (정상 경로 통합)
5. 마지막에 `/jobs/{jobId}/callback` 로 `SUCCESS` 또는 `FAILED` 송신

핵심: **enrichment 머지 로직은 한 곳(`enrichment-merge`)** 에서만 변경하면 정상 수집과 강제 재크롤 모두 동일하게 동작.

### 5.2 타임아웃

- 백엔드 `@Scheduled` (5분 주기) — `WHERE status IN ('PENDING','RUNNING') AND requested_at < now() - 5m` → FAILED + `error_message='timeout'`.
- 단일 인스턴스 가정. 멀티 인스턴스 도입 시 ShedLock 적용 — 별도 작업.

---

## 6. 프론트엔드 UI

### 6.1 페이지 구조

`AdminIngestionPage` 에 탭 시스템 도입.

```
AdminIngestionPage
├── Tab: "수집 현황"          (기존 KPI/일별 통계/실패 테이블)
└── Tab: "Enrichment 검토"   ← 신규
```

### 6.2 "Enrichment 검토" 탭 레이아웃

```
┌─────────────────────────────────────────────────────────────┐
│ Summary cards (4)                                           │
│  [전체 N]  [검토필요 N]  [실패 N]  [LITE N]                 │
├─────────────────────────────────────────────────────────────┤
│ Filters                                                     │
│  [✓ 검토필요만] [상태 ▼] [LITE ✓] [검색] [정렬 ▼]           │
├─────────────────────────────┬───────────────────────────────┤
│ 후보 목록 (테이블)           │ 사이드 패널 (행 클릭 시)      │
│ ─────────────────────────── │ ─────────────────────────────│
│ □ 제목      상태   신뢰도   │ 정책 #1234 "청년 ○○ 지원"     │
│ □ 청년주택  TOO_   0.42     │                              │
│            SHORT            │ ▼ Enrichment 현황            │
│ □ 일경험    FETCH_  -       │   status, confidence,        │
│            FAILED           │   sections 결측, fetchedAt   │
│                             │                              │
│                             │ ▼ Reference URLs             │
│                             │   [AUTO]/[ADMIN] 뱃지 + 편집 │
│                             │                              │
│                             │ ▼ 잡 이력 (최근 5)           │
│                             │                              │
│                             │ [재크롤 실행] (조건부 비활성) │
└─────────────────────────────┴───────────────────────────────┘
```

### 6.3 컴포넌트 (`frontend/src/pages/admin`)

| 파일 | 책임 |
|---|---|
| `AdminEnrichmentReviewTab.tsx` | 상위 — summary + filters + table + panel 조합 |
| `EnrichmentCandidateTable.tsx` | 후보 목록 테이블 (행 선택, 페이지네이션) |
| `EnrichmentReviewPanel.tsx` | 사이드 패널 — 상세/URL 편집/잡 이력/재크롤 버튼 |
| `EnrichmentReferenceSiteEditor.tsx` | URL 추가·삭제·source 뱃지 |
| `EnrichmentJobBadge.tsx` | PENDING/RUNNING/SUCCESS/FAILED 상태 칩 |

- API 호출은 `frontend/src/apis/adminEnrichment.ts` 로 묶음.
- react-query 기반, 기존 `useAdminIngestion` 패턴 따름.

### 6.4 폴링 정책

- **목록**: PENDING/RUNNING 잡이 1건 이상일 때만 폴링 활성. 폴링 간격 **3초**, 60초 후 **5초** 로 backoff. 모든 잡 종결 시 정지.
- **사이드 패널**: 열린 정책에 진행 중 잡이 있으면 동일 간격으로 별도 폴링. 패널 닫으면 해제.
- **완료 토스트**: 마지막으로 본 잡 상태와 비교해 SUCCESS/FAILED 로 바뀌면 토스트 1회.

### 6.5 어드민 입력 UX 안전장치

- URL 입력란은 클라이언트에서 `https?://` 형식 검증.
- 같은 URL 중복 입력은 즉시 인라인 에러.
- "재크롤 실행" 버튼은 다음 중 하나라도 해당하면 비활성:
  1. 같은 정책에 PENDING/RUNNING 잡 존재
  2. `referenceSites` 가 비어 있음
- 클릭 시 confirm 모달(정책 제목·실행될 URL 목록 표시) — 무심코 실행 방지.

### 6.6 권한·진입점

- 현재 `AdminIngestionPage` 의 admin role 가드 재사용.
- 사이드바·헤더 변경 없음 (같은 페이지 내 탭).

---

## 7. 에러 처리 매트릭스

| 케이스 | 동작 |
|---|---|
| n8n 다운 (백엔드→n8n 호출 실패) | 잡 즉시 FAILED, `error_message='n8n_unreachable'`. 어드민 화면에 에러 토스트 |
| n8n RUNNING 콜백 유실 | 잡 PENDING 으로 남았다가 5분 후 타임아웃 → FAILED |
| n8n 결과 콜백 유실 (ingestion 만 도착) | enrichment 는 갱신됐지만 잡은 5분 후 타임아웃. **fallback**: `enrichment.fetchedAt > requested_at` 이면 어드민 화면에 "결과 도착(콜백 유실)" 으로 분류 표시 |
| 어드민 입력 URL 자체가 4xx/5xx | enrichment-merge 가 부분 성공 처리 → status=LOW_CONFIDENCE 등으로 저장. 잡은 SUCCESS, 어드민이 다시 검토 후보로 봄 |
| URL 모두 실패 | `enrichment.status=FETCH_FAILED`. 잡은 SUCCESS (외부 사이트 응답일 뿐). 패널의 enrichment 진단 영역에 마지막 시도 결과 표시 |

---

## 8. 테스트 전략 (spring-test 컨벤션)

### 8.1 백엔드

| 레벨 | 대상 | 케이스 |
|---|---|---|
| 단위 | `EnrichmentReviewPolicy` (needsReview 판정) | enrichment=null / status≠OK / confidence<0.6 / LITE / 섹션 결측 2+ / 통과 — 6 케이스 |
| 단위 | `EnrichmentJobService.create` | 신규 PENDING 생성 / 중복 PENDING-RUNNING 거절(409) / URL override / 빈 referenceSites 거절 |
| 단위 | `EnrichmentJobService.complete` | SUCCESS·FAILED 콜백 처리, 멱등(이미 종료된 잡에 재콜백 시 무시) |
| 단위 | `PolicyReferenceSiteMerger` | ADMIN 입력 머지 — AUTO 보존, 동일 URL 승격, 중복 제거 |
| 슬라이스 (`@WebMvcTest`) | `AdminEnrichmentController` | 권한(ADMIN 아닌 사용자 403), 후보 목록 필터, summary 집계, 잡 생성 202, 409 |
| 통합 (`@SpringBootTest`) | 잡 라이프사이클 | PENDING→RUNNING→SUCCESS 전 흐름(콜백 시뮬레이션) + 5분 타임아웃 스케줄러 |

### 8.2 프론트엔드 (Vitest + Testing Library)

- `EnrichmentCandidateTable` — 필터 변경 시 쿼리 키 갱신, 정렬 토글
- `EnrichmentReviewPanel` — 재크롤 버튼 비활성 조건 4종
- `EnrichmentReferenceSiteEditor` — URL 형식 검증, 중복 인라인 에러, 저장 mutation
- 폴링 — PENDING/RUNNING 존재 시 활성, 모두 종결 상태가 되면 정지 (MSW + fake timer)

### 8.3 n8n

- `force-enrich` workflow fixture 추가 (`n8n/workflows/__fixtures__/force-enrich/`)
- URL override 동작, ingestion 콜백, `/jobs/{id}/callback` 송신 검증
- 기존 `enrich.mjs` / `verify.mjs` 패턴 따름

---

## 9. 운영 안전장치

- **타임아웃 스케줄러**: 5분 주기 (위 §5.2).
- **레이트 리밋**: 같은 정책에 1시간 내 잡 시도 5회 초과 시 429. 어드민 실수로 재크롤 폭주 방지.
- **로그**: 잡 상태 전환마다 구조화 로그 1줄 (`jobId`, `policyId`, `from`, `to`, `actor`, `elapsedMs`). 추후 알림·KPI 의 원천.
- **n8n 시크릿**: `INGESTION_CALLBACK_TOKEN` 환경변수 1개를 force-enrich 콜백·기존 ingestion 콜백 양쪽이 공유. `docs/OPS.md` 에 항목 추가.
- **권한**: 신규 `/admin/enrichment/**` 은 기존 `/admin/ingestion/**` 과 동일 admin role 가드 적용.

---

## 10. 마이그레이션 순서

각 단계가 독립 배포·롤백 가능.

1. **SQL #1** — `policy.reference_sites` JSONB 내 각 원소에 `"source":"AUTO"` 디폴트 추가
2. **SQL #2** — `enrichment_job` 테이블 생성 + partial unique index
3. **백엔드 배포** — 신규 API + 콜백. 기존 enrichment 동작에 영향 없음 (추가 컬럼 nullable, 하위 호환)
4. **n8n** `force-enrich` 워크플로우 배포
5. **프론트엔드** — 탭 추가 배포

---

## 11. 모듈 매핑

| 영역 | 위치 | 비고 |
|---|---|---|
| 도메인 모델 | `backend/.../policy/domain` | `PolicyReferenceSite.source` 확장, `EnrichmentJob` 신규 |
| 판정 로직 | `backend/.../policy/domain/EnrichmentReviewPolicy` | 순수 도메인 객체 |
| 잡 서비스 | `backend/.../policy/application/EnrichmentJobService` | 트랜잭션 경계 |
| 컨트롤러 | `backend/.../policy/api/AdminEnrichmentController` | 기존 admin 패키지 컨벤션 |
| n8n 호출 | `backend/.../policy/infrastructure/N8nForceEnrichClient` | RestClient + 시크릿 헤더 |
| 마이그레이션 | `backend/src/main/resources/sql/2026-05-21-*.sql` | 기존 파일 명명 컨벤션 |
| 프론트 페이지 | `frontend/src/pages/admin/AdminEnrichmentReviewTab.tsx` 외 | §6.3 |
| API 클라이언트 | `frontend/src/apis/adminEnrichment.ts` | react-query hooks |
| n8n 워크플로우 | `n8n/workflows/force-enrich.json` + `__fixtures__/force-enrich/` | enrichment-merge 재사용 |

---

## 12. Open Questions (구현 단계에서 결정)

- 어드민 식별자 형식 (이메일 vs user id) — 현재 admin role 인증 구현 확인 후 결정
- `requested_urls` JSONB 의 정확한 스키마 — name 도 함께 저장할지, url 만 저장할지
- 폴링: 탭이 백그라운드일 때 `visibilitychange` 로 일시 정지 + 복귀 시 즉시 refetch 추가 여부 (§6.4 의 3초/5초 backoff 와는 독립)
