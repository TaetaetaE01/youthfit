# 어드민 정책 처리 현황 대시보드 설계 (Draft / Brainstorming 시작점)

> **상태**: draft (2026-05-29 작성) — brainstorming 으로 보강 필요
> **선행**: [2026-05-28-policy-enrichment-tracking-design.md](./2026-05-28-policy-enrichment-tracking-design.md) Phase E
> **모듈**: `admin` (백엔드 + 프론트엔드), `policy`, `rag`, `ingestion`
> **관련 이슈**: [#126](https://github.com/TaetaetaE01/youthfit/issues/126)

## 1. 배경 / 동기

2026-05-28 Phase A 가 완료되어 `policy_processing_step` 테이블 + 5단계 추적 인프라가 백엔드에 구축됨 (id 86~90 정책 5건 수동 검증으로 step 기록 정상 확인). 그러나 그 추적 정보를 **운영자가 한눈에 볼 수 있는 어드민 UI** 가 아직 없다.

사용자가 운영하면서 알고 싶은 것은 단순한 "단계별 SUCCESS/FAILED 표" 이상이다. 정책의 **데이터 품질** 까지 한 화면에서 보고 싶음:

1. **모든 정책의 5단계 진행 상황** — 어느 정책이 RAG 까지 갔고, 어느 정책이 GUIDE 에서 막혔는지.
2. **참고 사이트(reference site) 크롤링 여부** — n8n 워크플로우가 enrichment 단계에서 참고사이트 들어가서 풍부한 정보를 가져왔는지 (SPA 라 skip 됐는지, fetch 실패했는지). 정책마다 다름.
3. **첨부파일 임베딩 여부** — 정책의 첨부파일들이 다운로드·텍스트 추출·RAG 임베딩까지 갔는지. 어드민이 부족한 정책은 보강이 필요한지 판단.

본 spec 은 이 3가지 정보를 통합 대시보드로 제공하는 설계의 **brainstorming 시작점** 이다. 세부 결정은 다음 세션의 `superpowers:brainstorming` 사이클에서 정한다.

## 2. 사용자 요구사항 (raw)

다음은 사용자가 명시한 요구사항. 그대로 보존:

1. "모든 정책들의 5단계 성공했는지 알려주는 어드민 페이지"
2. "추가 사이트도 크롤링했는지 여부"
3. "첨부파일도 확인해서 임베딩했는지에 대한 여부"

→ 핵심: 정책 단위로 "이 정책이 사용자에게 보여줄 만큼 풍부한가" 를 한 화면에서 판단할 수 있어야 함.

## 3. 기존 인프라와의 관계 (재사용 가능한 것)

### 백엔드 (이미 있음)
- `policy_processing_step` 테이블 — Phase A 가 추가. 5단계 (INGESTION/ENRICHMENT/GUIDE/RULE/RAG_INDEXING) 의 status + reason + duration + attempt 보관.
- `policy_attachment` 테이블 — 첨부의 다운로드/추출 상태 (`extraction_status`: PENDING/DOWNLOADING/DOWNLOADED/EXTRACTED/FAILED/SKIPPED).
- `policy_document` 테이블 — RAG 청크. `source` 컬럼이 BODY/ATTACHMENT/ENRICHMENT_BODY 구분. 정책별 첨부 임베딩 여부는 `WHERE policy_id=? AND source='ATTACHMENT'` count 로 확인 가능.
- `enrichment_job` 테이블 — 어드민 강제 enrichment 잡 이력.
- `policy.enrichment` JSONB 컬럼 — 이미 적재된 enrichment 결과 (sections, cleanedText, extraAttachments, sourceUrl 등).
- `/admin/enrichment/*` 엔드포인트 (`AdminEnrichmentApi`) — 후보 목록, 상세, 참조 사이트 갱신, 강제 잡 생성, 잡 조회.

### 프론트엔드 (이미 있음)
- `pages/admin/AdminIngestionPage.tsx` — Ingestion + Enrichment 탭.
- `pages/admin/enrichment/EnrichmentCandidateTable.tsx` — needsReview 후보 표.
- `pages/admin/enrichment/EnrichmentReviewPanel.tsx` — 정책별 enrichment 상세 패널.
- `pages/admin/enrichment/EnrichmentReferenceSiteEditor.tsx` — 참조 사이트 직접 편집.

### 아직 없는 것 (본 spec 의 추가 범위)
- 5단계 step (`policy_processing_step`) 을 보여주는 admin endpoint + UI
- 정책 단위 통합 뷰: 5단계 status + 첨부 임베딩 status + 참조사이트 fetch 결과 (Phase D 후) 를 한 행에 표시
- 단계별 FAILED 만 모아보는 필터
- 수동 retry (특정 정책의 특정 단계 재실행)

## 4. 개념적 정보 모델

대시보드 1행 (정책 1건) 에 모아 보여줄 신호 — brainstorming 단계에서 우선순위·표시 형식 결정:

| 신호 | 출처 | 가능한 값 |
|------|------|-----------|
| INGESTION status | `policy_processing_step` | SUCCESS / SKIPPED(DUPLICATE) / FAILED |
| ENRICHMENT status | 동일 | SUCCESS / SKIPPED / FAILED / 없음 |
| ENRICHMENT 참고사이트 결과 | `policy_processing_step.detail_json.skippedUrls` (Phase D 후) | URL 별 SUCCESS / SPA_DETECTED / TIMEOUT / HTTP_4XX |
| GUIDE status | `policy_processing_step` | SUCCESS / FAILED |
| RULE status | 동일 | SUCCESS / FAILED |
| RAG_INDEXING (본문) status | 동일 | SUCCESS / SKIPPED(EMPTY_BODY) / FAILED |
| 첨부 다운로드 status | `policy_attachment.extraction_status` 집계 | "0/0" or "3/5 DOWNLOADED" |
| 첨부 텍스트 추출 status | 동일 | "2/3 EXTRACTED, 1 FAILED" |
| 첨부 RAG 임베딩 여부 | `policy_document` count where `source='ATTACHMENT'` | true (chunks > 0) / false |
| 종합 완성도 | derived | "완전 / 부분 / 미흡" (RAG 본문 SUCCESS + 첨부 임베딩 완료시 완전) |

→ 모든 정보가 이미 DB 에 있다. **새 데이터 모델 추가는 거의 필요 없음.** API + UI 가 핵심.

## 5. brainstorming 시 결정해야 할 사항

다음 세션에서 `superpowers:brainstorming` 으로 다룰 항목:

**B1. 페이지 위치/구조**
- 기존 `/admin/ingestion` 의 새 탭 ("처리 현황") 으로 / `/admin/enrichment` 와 통합 / 신규 `/admin/policies/processing` 페이지
- 정책 검색·필터 UI 의 패턴 (기존 `EnrichmentCandidateTable` 와 일관성)

**B2. 행 단위 표시 밀도**
- 정책당 1행 (모든 신호 column 으로 펼침) — 정보 밀도 높음, 좌우 스크롤 부담
- 정책당 1행 + 클릭시 펼침 (드릴다운) — 1차 한눈에 + 2차 상세
- 정책당 카드형 — 모바일 친화적이나 한 화면 정보 부족

**B3. "완성도" 정의**
- "RAG 본문 SUCCESS" 만 — 가장 단순, 사용자 노출 기준과 일치 (Phase B 의 processing_status READY)
- "RAG 본문 SUCCESS + 첨부 다운로드 100% + 첨부 임베딩 ≥ 1" — 데이터 풍부함 기준
- "+ ENRICHMENT 까지" — 본문 정제 결과까지

**B4. 첨부 임베딩 검증 방식**
- `policy_attachment` 의 `extraction_status='EXTRACTED'` 카운트 vs `policy_document` 의 `source='ATTACHMENT'` 카운트 비교
- 둘이 일치하지 않으면 → 임베딩 누락 (추출은 됐는데 RAG 인덱싱 안 됨)

**B5. 수동 액션 (재실행) 범위**
- step 별 재실행 — Phase E 의 `POST /admin/policies/{id}/processing-steps/{step}/retry` 와 일치
- 첨부 임베딩 재실행 — `AttachmentReindexService` 트리거 endpoint
- 정책 전체 재처리 — 모든 단계 다시 큐잉

**B6. 어드민 시야의 우선순위 필터**
- "RAG_INDEXING FAILED 만" — 사용자 노출 안 된 정책 (긴급)
- "ENRICHMENT.skippedUrls 비지 않음" — n8n 단계 데이터 품질 이슈
- "첨부 EXTRACTED 되지만 RAG 안 됨" — 임베딩 누락
- "guide/rule FAILED" — 부가 기능 빠짐 (우선순위 낮음)

**B7. Phase D (n8n 개편) 와의 의존성**
- 참고사이트 fetch 결과 (`skippedUrls`) 는 Phase D 가 끝나야 의미 있는 데이터가 채워짐
- 그전까지는 column 만 마련하고 "데이터 없음" 표시
- 대시보드를 Phase D 와 동시에? 먼저? 나중에?

## 6. 출발점 정리 (다음 세션이 받을 수 있는 것)

다음 세션에서 brainstorming → spec → plan → 구현 사이클을 돌리려면:

1. **본 draft 를 읽고** 사용자 요구사항 (Section 2) + 기존 인프라 (Section 3) + 정보 모델 (Section 4) 확인.
2. **Section 5 의 B1~B7 을 brainstorming 으로 결정** 후 정식 spec 으로 승격 (`2026-05-29-admin-policy-processing-dashboard-design.md` 를 갱신).
3. **plan 작성** → phase 분할 (백엔드 endpoint → 프론트엔드 UI → 수동 액션).
4. **구현** → PR.

## 7. 본 세션 (2026-05-29) 까지의 상태 요약

- Phase A 백엔드 추적 인프라 완료 (9 commits, `1d59f01..6d67fee`).
- 정책 86~90 적재로 수동 검증 통과 (5단계 중 4단계 — INGESTION/GUIDE/RULE/RAG_INDEXING — 모두 SUCCESS, ENRICHMENT 는 워크플로우가 payload 안 보냄).
- `AdminIngestionService` 의 `Instant → LocalDateTime` 캐스팅 버그 1건 발견·수정 (Phase A 와 무관한 기존 버그, 별도 commit 예정).
- **본 spec 은 brainstorming 시작점** — section 5 의 결정 사항이 명확해진 뒤 정식 spec 으로 완성.
