# 어드민 — Spec 5: Ingestion 헬스 설계

> **상태**: Spec 확정 (2026-05-06 brainstorming 완료)
> **작성일**: 2026-05-05 (파일명 유지)
> **시리즈**: 어드민 시리즈 5개 중 #5
> **선행**: Spec 1 (admin foundation) DONE, Spec 2 (admin email tracking) DONE, Spec 3 (admin qna cache log) DONE

---

## 1. 목표

n8n 및 외부 수집 파이프라인의 데이터 신선도와 품질을 운영적으로 추적한다.
- **집계**: 일자별 신규 수신 / 정규화 성공·실패 / 중복 비율
- **건별**: 정규화 실패한 정책 — 어떤 원천에서, 무슨 이유로 실패했는지
- **알람 후보**: "마지막 24h 동안 수신 없는 source" 식별
- **재처리**: 실패 항목 어드민에서 재시도 트리거 (도메인 API 호출 경유)

## 2. 범위

### In
- `IngestionRunLog` 엔티티 (수신 이벤트 단위 집계) + `IngestionItemFailure` 엔티티 (개별 실패 항목)
- 기존 `IngestionService.receivePolicy(...)` 흐름에 try/catch/finally 적재 hook 추가
- 어드민 화면:
  - 상단 알람 영역 — 24h 미수신 source 리스트 (있을 때만)
  - KPI 4개 (어제 신규 / 어제 실패 / 7일 평균 신규 / 7일 평균 실패율)
  - Stacked bar — 일자별 신규/실패/중복 (source 색상)
  - 원천별 테이블 — 마지막 수신 시각, 7일 합계, 실패율
  - 실패 항목 리스트 + 필터(원천, 사유) + 페이지네이션
  - 실패 상세 — raw_payload (JSON pretty print), 사유, 에러 메시지
  - 재처리 액션 (단건) — `ingestion` 도메인 use case 호출
- `raw_payload` 7일 후 SHA-256 hash로 redact + 30일 후 `IngestionItemFailure` 행 삭제 (`IngestionRunLog`는 무기한)
- "마지막 수신 없음" 임계 24h 고정 (source별 설정은 v1)

### Out
- 크롤링 트리거 / n8n 워크플로우 직접 제어 (어드민에서 외부 시스템 수정 X)
- 정책 데이터 직접 편집 — 별도 컨텐츠 관리 spec
- 일괄 재처리 (bulk retry) — 단건 재처리만 (대량 재처리는 n8n 재실행으로 처리)
- 수동 매핑 화면 (실패 항목을 수정해 다시 매핑) — v1 검토
- 신규 dedup 로직 / 중복 판정 기준 — 기존 ingestion 도메인 결과를 카운트만 반영
- 실시간 알림 (수신 중단 push) — 외부 모니터링 (Grafana 등) 책임

## 3. 핵심 결정 로그

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| 1 | 적재 위치 | 기존 `IngestionService.receivePolicy(...)` try/catch/finally hook 추가 | 신규 도메인 모듈 추가 부담 큼. ingestion 모듈 내부에 적재 책임 두는 것이 자연스러움 |
| 2 | 데이터 모델 분리 | `IngestionRunLog` (run 단위 집계) + `IngestionItemFailure` (실패 단건) 2 테이블 | run 통계 조회와 실패 디버깅의 액세스 패턴이 달라 분리. JSONB 컬럼은 실패 행에만 필요 |
| 3 | `raw_payload` 저장 | DB JSONB | 디버깅 즉시성 우선. 양 부담은 7일 후 hash redact + 30일 행 삭제로 통제. S3 ref는 외부 의존 1개 더 — 디버깅 1회 ROI 낮음 |
| 4 | `raw_payload` PII | 어드민 계정 보호 + 7일 자동 redact | 정책 외부 데이터라 PII 위험 낮음. 0은 아니므로 redact + 행 삭제 조합 |
| 5 | 재처리 액션 | `ingestion` 도메인에 `RetryFailedIngestionItemUseCase` 추가 → admin controller가 application service로 호출 | DDD 레이어링 유지. admin은 조회·트리거만, 비즈니스 로직은 ingestion 소유. admin → ingestion 단방향 의존 |
| 6 | "마지막 수신 없음" 임계 | 24h 고정 | source별 설정은 운영 데이터 누적 후 v1. KISS |
| 7 | 중복 판정 기준 | 기존 `IngestionService` dedup 로직 결과를 `IngestionRunLog.duplicate_count`에 카운트만 반영 | 신규 판정 로직 도입 X. 도메인 결과를 그대로 관측 |
| 8 | 적재 트리거 | 동기 적재 (run 단위 집계는 호출당 1회, 부담 낮음) | run 통계는 ingestion 호출당 1 row. async overhead 불필요. 실패 항목은 run 내 이미 발생한 예외 처리에 묶임 |
| 9 | run 단위 정의 | `IngestionService.receivePolicy(...)` 1회 호출 = 1 run (정책 1건 단위) | n8n이 정책 단위로 호출. 배치 단위 집계는 n8n run id가 외부에서 전달돼야 함 — v1 검토 |
| 10 | source 식별 | `source` 컬럼 (string, 예: `youth-center`, `gov24`) — n8n 호출 시 헤더 또는 payload에 포함 | ingestion API 변경 필요 (선행). plan 단계에서 현재 API 시그니처 확인 후 결정 |

## 4. 데이터 모델

### 4.1 엔티티 — `ingestion/domain/model/IngestionRunLog`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | `BIGINT PK` | auto |
| `source` | `VARCHAR(40)` | 외부 원천 식별자 (예: `youth-center`, `gov24`, `unknown`) |
| `received_count` | `INT` | run에서 받은 건수 (정책 1건이면 1) |
| `normalized_success_count` | `INT` | 정규화 성공 건수 |
| `normalized_failure_count` | `INT` | 정규화 실패 건수 |
| `duplicate_count` | `INT` | dedup 결과 중복 판정 건수 |
| `received_at` | `TIMESTAMP` | run 진입 시각 |
| `processed_at` | `TIMESTAMP` | run 종료 시각 |
| `duration_ms` | `INT` | `processed - received` ms |
| `created_at` | `TIMESTAMP` | DB 적재 시각 |

### 4.2 엔티티 — `ingestion/domain/model/IngestionItemFailure`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | `BIGINT PK` | auto |
| `run_log_id` | `BIGINT` | FK 없이 단순 컬럼 (보관 정책 차이로 무결성 비강제) |
| `source` | `VARCHAR(40)` | run의 source 복사 (조회 편의) |
| `source_item_id` | `VARCHAR(120)` nullable | 외부 시스템의 식별자 (있으면) |
| `raw_payload` | `JSONB` nullable | 7일 후 hash로 redact (또는 null) |
| `raw_payload_hash` | `VARCHAR(64)` nullable | redact 후 SHA-256 hex (재시도 매칭용) |
| `failure_reason` | `VARCHAR(30)` enum | `VALIDATION` \| `PARSING` \| `MAPPING` \| `DEDUPLICATION_CONFLICT` \| `OTHER` |
| `error_message` | `TEXT` | 짧게 (예외 메시지 + 짧은 stack 1줄) |
| `retry_count` | `INT` default 0 | 어드민에서 재시도한 횟수 |
| `last_retried_at` | `TIMESTAMP` nullable | 마지막 재시도 시각 |
| `created_at` | `TIMESTAMP` | DB 적재 시각 |

### 4.3 인덱스
- `idx_ingestion_run_log_received_at` on `(received_at DESC)`
- `idx_ingestion_run_log_source_received_at` on `(source, received_at DESC)`
- `idx_ingestion_item_failure_created_at` on `(created_at DESC)`
- `idx_ingestion_item_failure_source_reason_created_at` on `(source, failure_reason, created_at DESC)`

### 4.4 enum — `ingestion/domain/model/FailureReason`

```java
public enum FailureReason {
  VALIDATION,             // 필수 필드 누락 / 형식 오류
  PARSING,                // JSON 파싱 실패
  MAPPING,                // 도메인 엔티티 매핑 실패
  DEDUPLICATION_CONFLICT, // dedup 키 충돌 (동일 정책 다른 출처 등)
  OTHER                   // 분류 안 됨
}
```

### 4.5 마이그레이션

신규 SQL 파일 1개: `2026-05-05-ingestion-health.sql` (두 테이블 묶음). 백필 없음 — 과거 ingestion 호출은 추적 불가.

## 5. 아키텍처 & 적재 흐름

### 5.1 모듈 경계

| 위치 | 책임 |
|---|---|
| `ingestion/domain/model/IngestionRunLog` | 엔티티 |
| `ingestion/domain/model/IngestionItemFailure` | 엔티티 |
| `ingestion/domain/model/FailureReason` | enum |
| `ingestion/infrastructure/repository/IngestionRunLogRepository` | JPA repo |
| `ingestion/infrastructure/repository/IngestionItemFailureRepository` | JPA repo |
| `ingestion/application/service/IngestionService` | 적재 hook 추가 (수정) |
| `ingestion/application/service/RetryFailedIngestionItemUseCase` | 재처리 use case (신규) |
| `ingestion/infrastructure/scheduler/IngestionRedactScheduler` | 7일 redact + 30일 삭제 |
| `admin/presentation/controller/AdminIngestionController` | 조회 + 재처리 트리거 |
| `admin/presentation/dto/response/Ingestion*Response` | 어드민 응답 DTO |
| `admin/application/service/AdminIngestionService` | 조회 + 집계 + 재처리 dispatch |

### 5.2 적재 흐름

```
n8n → POST /api/internal/ingest/policy { source, payload }
  └─ IngestionService.receivePolicy(IngestPolicyCommand)
       ├─ runStart = Instant.now()
       ├─ try {
       │    parse → mapCategory/Period/Sections → policyIngestionService.registerPolicy(...)
       │    success counts (received=1, success=1 or duplicate=1) ← PolicyIngestionResult.duplicate 여부 판정
       │  }
       │  catch (validation/parsing/mapping/dedup conflict 등) {
       │    failure counts (failure=1)
       │    IngestionItemFailureRepository.save(IngestionItemFailure { source, source_item_id, raw_payload, reason, error_message })
       │    rethrow 현행 동작 유지 (controller가 5xx/4xx 응답 결정)
       │  }
       └─ finally {
            runEnd = Instant.now()
            IngestionRunLogRepository.save(IngestionRunLog { source, received=1, success/failure/duplicate, runStart, runEnd, duration })
          }
```

> `receivePolicy` 의 트랜잭션 경계와 예외 전파 정책은 plan 단계에서 현재 코드 검토 후 명시. 적재 자체가 사용자 핫패스에 있지 않으므로 동기 OK. 기존 `eventPublisher.publishEvent(PolicyUpsertedEvent)` 와 `triggerAttachmentDownload` 는 그대로 유지.

### 5.3 재처리 흐름

```
admin POST /api/v1/admin/ingestion/failures/{id}/retry
  └─ AdminIngestionService.retryFailure(failureId)
       └─ RetryFailedIngestionItemUseCase.retry(failureId)
            ├─ IngestionItemFailure 조회
            ├─ raw_payload null 또는 redact 됐으면 → 400 "재처리 불가 (7일 경과)"
            ├─ IngestionService.ingestPolicy(failure.source, failure.raw_payload)
            ├─ 성공 시: failure.retry_count += 1, last_retried_at = now()
            ├─ 실패 시: 신규 IngestionItemFailure 적재 (체인이 아닌 별도 row)
            └─ 응답: { result: success | failure, message }
```

### 5.4 알람 (24h 미수신 source) 산출

```sql
-- AdminIngestionService.findStaleSources()
SELECT source, MAX(received_at) AS last_received_at
FROM ingestion_run_log
GROUP BY source
HAVING MAX(received_at) < now() - interval '24 hours'
```

> source 목록은 동적 (테이블에서 발견된 source 기준). 신규 source는 첫 수신 후 등록.

## 6. 어드민 화면

### 6.1 라우트
- `/admin/ingestion` — 메인
- `/admin/ingestion/failures/:failureId` — 실패 상세

### 6.2 메인 화면 구성

**상단 알람 영역** (있을 때만)
- 노란/주황 배경 배너: "마지막 24h 동안 수신 없는 source: youth-center, gov24"
- 비어있으면 알람 영역 자체 숨김 (zero-state로 부재 강조 X)

**KPI 카드 4개** (Spec 2 `KpiCard` 재사용)
- 어제 신규 (총 receive_count, 전일 대비 ↑↓)
- 어제 실패 (총 failure_count, 전일 대비 ↑↓)
- 7일 평균 신규/일
- 7일 평균 실패율 (%)

**Stacked bar — 일자별 신규/실패/중복** (Spec 2 `StackedBarChart` 재사용)
- 14일 윈도우, 3색 적층 (성공 / 실패 / 중복)
- source 색상 분리는 **하단 테이블에서** 확인 — 차트 복잡도 통제

**원천별 테이블**
- 컬럼: source / 마지막 수신 시각 / 7일 신규 합계 / 7일 실패율 / 상태 (24h 미수신 시 빨강 뱃지)
- 정렬: 마지막 수신 시각 오래된 순

**실패 항목 리스트 + 필터**
- 컬럼: 시각 / source / 사유 뱃지 / source_item_id / error_message 발췌 / 재시도 횟수 / 액션 (재처리 버튼 / 상세 링크)
- 필터: source, failure_reason, 기간
- 페이지네이션: 20건/페이지, Spec 2 `Pagination` 재사용
- 행 단위 재처리 버튼: confirm modal → POST `/retry` → 결과 toast

### 6.3 실패 상세

- 메타: 시각 / source / 사유 뱃지 / source_item_id / 재시도 횟수 / 마지막 재시도 시각
- raw_payload: JSON pretty print (`<pre>` 박스, 7일 redact 후엔 hash + "redacted" 안내)
- error_message 전문 (`<pre>`)
- 재처리 버튼 (raw_payload 살아있을 때만 활성)

### 6.4 API 엔드포인트

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/v1/admin/ingestion/kpi` | `IngestionKpiResponse` |
| GET | `/api/v1/admin/ingestion/daily-stats?days=14` | `List<IngestionDailyStatsResponse>` |
| GET | `/api/v1/admin/ingestion/sources` | `List<IngestionSourceSummaryResponse>` (마지막 수신 시각 + 7일 합계 + 실패율) |
| GET | `/api/v1/admin/ingestion/stale-sources` | `List<IngestionStaleSourceResponse>` (24h 미수신) |
| GET | `/api/v1/admin/ingestion/failures?source=&reason=&from=&to=&page=&size=` | `Page<IngestionFailureSummaryResponse>` |
| GET | `/api/v1/admin/ingestion/failures/{id:\\d+}` | `IngestionFailureDetailResponse` |
| POST | `/api/v1/admin/ingestion/failures/{id:\\d+}/retry` | `IngestionRetryResponse` `{ result, message, newFailureId? }` |

인증: Spec 1의 `@RequireAdmin` 적용.

### 6.5 응답 DTO 명명 (Spec 2~3 패턴 답습)

- `IngestionKpiResponse`
- `IngestionDailyStatsResponse` (`{ date, source, receivedCount, failureCount, duplicateCount }`)
- `IngestionSourceSummaryResponse` (`{ source, lastReceivedAt, sevenDayReceived, sevenDayFailureRate }`)
- `IngestionStaleSourceResponse` (`{ source, lastReceivedAt, hoursSinceLastReceived }`)
- `IngestionFailureSummaryResponse` (`{ id, source, reason, sourceItemId, errorMessageExcerpt, retryCount, createdAt }`)
- `IngestionFailureDetailResponse` (`{ ...summary, rawPayload?, rawPayloadHash?, errorMessage, lastRetriedAt? }`)
- `IngestionRetryResponse` (`{ result: "SUCCESS" | "FAILURE", message, newFailureId? }`)

### 6.6 프론트 파일

- `frontend/src/pages/admin/AdminIngestionPage.tsx`
- `frontend/src/pages/admin/AdminIngestionFailureDetailPage.tsx`
- `frontend/src/apis/admin.ingestion.api.ts`
- `frontend/src/components/admin/ingestion/*` (StaleSourceBanner, SourceTable, FailureTable 등 페이지 한정)

### 6.7 사이드바

`frontend/src/components/layout/AdminSidebar.tsx`에 `/admin/ingestion` 항목 추가 (`soon: true` 없이 활성화).

## 7. 환경 변수 / 설정

| 키 | 기본값 | 의미 |
|---|---|---|
| `youthfit.ingestion.health.payload-redact-days` | `7` | raw_payload hash로 redact까지 일수 |
| `youthfit.ingestion.health.failure-retention-days` | `30` | IngestionItemFailure 행 보관일 |
| `youthfit.ingestion.health.stale-threshold-hours` | `24` | "마지막 수신 없음" 임계 시간 |

> `IngestionRunLog`는 무기한 (트렌드 분석 가치, 부피 적음 — run 1개당 1 row). 별도 retention 키 없음.

## 8. 보관 정책 / 운영

- `IngestionRunLog`: 무기한
- `IngestionItemFailure`: 30일 (행 삭제), 7일 후 raw_payload redact (hash로 치환, raw_payload는 NULL)
- 일일 스케줄러 (`IngestionRedactScheduler`) — 매일 03:00 KST:
  1. `UPDATE ingestion_item_failure SET raw_payload = NULL, raw_payload_hash = sha256(...) WHERE created_at < now() - interval '7 days' AND raw_payload IS NOT NULL`
  2. `DELETE FROM ingestion_item_failure WHERE created_at < now() - interval '30 days'`
- 재처리 액션은 **단건만**. 일괄 재처리는 n8n 측 재실행 권장 (어드민 UI 부담 + 사고 위험)
- 별도 `operations/*-runbook.md` 작성하지 않음 (외부 의존 없음, 신규 환경 변수 default 안전). plan 의 "후속/미결" 섹션에 운영 메모 (재처리 SOP, redact 절차).

## 9. 테스트 전략

### 9.1 백엔드 단위
- `IngestionService.ingestPolicy()` 적재 hook — 성공 시 run_log 1건 + 0 failure / 실패 시 run_log 1건 + 1 failure / dedup 시 run_log 1건 + 0 failure with duplicate=1
- `FailureReason` 매핑 — 예외 타입별 enum 분기 (예: `IllegalArgumentException` → `VALIDATION`, `JsonProcessingException` → `PARSING` 등 — 매핑 규칙 plan 단계에서 명시)
- `RetryFailedIngestionItemUseCase` — raw_payload 있음/없음, 성공/재실패 분기, retry_count 증가

### 9.2 백엔드 슬라이스 / 통합
- `IngestionRedactScheduler` — Testcontainers (Postgres) 7일/30일 + 1일 fixture 셋업 후 호출 → redact + 삭제 검증 (TTL 단축 설정으로)
- `AdminIngestionController` 슬라이스 (`@WebMvcTest + @WithMockUser(roles="ADMIN")`):
  - 6개 GET 엔드포인트 200, 필터/페이지네이션
  - POST `/retry` — failure_id 존재/부재, raw_payload null 시 400, 비ADMIN 401/403
  - id 숫자 제약 (정규식 `\d+`) 위반 시 400

### 9.3 백엔드 E2E
- 정책 1건 ingest 성공 → IngestionRunLog 1건, IngestionItemFailure 0건
- 정책 1건 ingest 실패 (정형 fixture) → IngestionRunLog 1건 (failure=1), IngestionItemFailure 1건
- 어드민 재처리 → IngestionRunLog 1건 추가 (재시도 run), failure 갱신 또는 신규 failure
- "24h 미수신" 알람 — fixture로 25h 전 마지막 receive → stale-sources에 등장 검증

### 9.4 프론트엔드
- 컴포넌트: StaleSourceBanner (zero state hide / 1개 / 다수), SourceTable 정렬, FailureTable + 필터, 재처리 confirm modal, 사유 뱃지 색상 (5종)
- 페이지 통합: 필터 변경 → API 재호출 + 페이지네이션 reset, 재처리 버튼 → POST → toast + 리스트 갱신
- 상세 페이지: raw_payload 살아있을 때 / redact 됐을 때 (hash + "redacted" 안내) 분기 렌더링

### 9.5 커버리지
- 신규 백엔드 코드 라인 커버리지 80%
- 프론트는 페이지·컴포넌트 단위 happy path + 에러 상태 1건씩

### 9.6 검증 커맨드 (plan 단계에서 정확한 명령어 명시)
- `./gradlew test`
- `cd frontend && npm run test`
- `cd frontend && npm run typecheck && npm run lint`

## 10. 의존성

- Spec 1 (admin foundation) DONE — 사이드바, `@RequireAdmin`, `/api/v1/admin/**` 라우트
- Spec 2 (admin email tracking) DONE — Recharts, `KpiCard`, `StackedBarChart`, `Pagination`, 응답 DTO 명명 규칙, confirm modal 패턴
- 기존 `IngestionService` — 적재 hook 추가 (수정)
- 기존 ingestion API (`POST /api/internal/ingest/policy` 등) — `source` 파라미터 검토 (plan 단계에서 현재 시그니처 확인)
- Spec 4 (LLM 비용)와 무의존 (병렬 진행 가능)

## 11. 변경 영향 범위

### 11.1 신규
- `ingestion/domain/model/IngestionRunLog`, `IngestionItemFailure`, `FailureReason`
- `ingestion/infrastructure/repository/IngestionRunLogRepository`, `IngestionItemFailureRepository`
- `ingestion/application/service/RetryFailedIngestionItemUseCase`
- `ingestion/infrastructure/scheduler/IngestionRedactScheduler`
- `admin/presentation/controller/AdminIngestionController`, `AdminIngestionApi`
- `admin/application/service/AdminIngestionService`
- `admin/presentation/dto/response/Ingestion{Kpi,DailyStats,SourceSummary,StaleSource,FailureSummary,FailureDetail,Retry}Response`
- SQL 마이그레이션: `2026-05-05-ingestion-health.sql` (두 테이블)
- 프론트 페이지/컴포넌트/API 함수 (§ 6.6)

### 11.2 수정
- `ingestion/application/service/IngestionService.receivePolicy(...)` — try/catch/finally + 적재 hook
- 기존 ingestion API (필요시) — `source` 파라미터 추가/검증
- `frontend/src/components/layout/AdminSidebar.tsx` — `/admin/ingestion` 항목 추가
- `application.yml` — 신규 설정 키 3개
- `App.tsx` 또는 라우터 — `/admin/ingestion`, `/admin/ingestion/failures/:id` 라우트 추가

## 12. 위험 / 트레이드오프

| 위험 | 완화 |
|---|---|
| `ingestPolicy` 적재 hook으로 인한 성능/장애 | 동기 적재지만 호출당 1 row insert만 추가 — 부담 낮음. try/catch로 적재 실패가 ingestion 본 흐름 차단하지 않도록 격리 |
| raw_payload PII 잔존 | 어드민 계정 보호 + 7일 redact + 30일 삭제. 정책 외부 데이터라 위험 낮으나 0 아님 |
| 실패 분류 enum 매핑 부정확 | OTHER fallback + 운영 데이터 누적 후 enum 갱신. plan에 매핑 규칙 명시 |
| 재처리로 인한 부작용 (멱등성) | `IngestionService` 자체가 dedup 갖고 있으므로 멱등 가정. 재처리 결과 별도 IngestionRunLog 발생으로 추적 가능 |
| dedup 카운트 부정확 | 기존 도메인 결과를 그대로 카운트. 도메인 로직 변경되면 수치도 자연 변경 — 운영 메타로 감안 |
| source 식별 불가 (n8n 호출 시 누락) | `unknown` source로 적재 + warn 로그. plan 단계에서 ingestion API 시그니처 검토 후 source 필수화 결정 |
| `IngestionRunLog` 무기한 증가 | run 1개당 1 row. 정책 수신 일 100건 가정 시 연 36,500 row — 부담 적음. 5년 후 재검토 (v1) |
| 재시도 raw_payload 만료 (7일 경과) | UI에서 재처리 버튼 비활성. 명시 안내 ("7일 경과로 raw 데이터 없음") |

## 13. 후속 / 비범위

- source별 "마지막 수신 없음" 임계 설정 (24h vs 1주 등) — 운영 데이터 누적 후 v1
- 일괄 재처리 (bulk retry) — 사고 위험 검토 후 v1
- 수동 매핑 화면 (실패 raw 수정 후 재시도) — UX 결정 많음, v1
- raw_payload S3 이전 — DB 부담 측정 후 v1
- n8n run id 추적 (배치 단위 집계) — n8n 측 변경 필요, v1
- 실시간 알림 (수신 중단 push, Slack 등) — 외부 모니터링 (Grafana 등) 책임
- Micrometer 메트릭 노출 (실패율 게이지 등)
- 신규 source 자동 등록 알람 (예상 못한 source 첫 수신 시 알림)

---

## 부록: 시리즈 5개 spec 간 공통 사항

| 항목 | 결정 메모 |
|---|---|
| 인증/라우팅 | Spec 1 결정 (`/api/v1/admin/**`, `@RequireAdmin`) |
| ReadModel 패턴 | admin 모듈은 조회·트리거만; 데이터는 각 도메인이 적재 (본 spec은 ingestion 모듈 적재 + ingestion use case 호출) |
| 차트 라이브러리 | **Recharts** (Spec 2 결정, 본 spec 답습) |
| 보관 정책 | 본 spec — RunLog 무기한, ItemFailure 30일 / payload 7일 redact (§ 8) |
| 디자인 토큰 | Spec 1 다크 사이드바 + 브랜드 indigo (`frontend/src/index.css`) |
