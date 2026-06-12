# 정책 enrichment 강화 및 처리 단계 추적 설계

> **상태**: spec (2026-05-28 작성)
> **모듈**: `ingestion`, `policy`, `guide`, `eligibility`, `rag`, `admin` (백엔드) / `youth-seoul-crawl` (n8n) / 사용자·어드민 페이지 (프론트엔드)
> **관련 이슈**: [#126 RAG 1차 인덱싱 리스너 미동작](https://github.com/TaetaetaE01/youthfit/issues/126)

## 1. 배경 / 동기

청년몽땅정보통 (`youth.seoul.go.kr`) 크롤링으로 적재된 정책 5건 (id 81~85) 의 품질 검토 결과 다음 격차가 확인됨:

1. **본문 정보 부족**: 사이트 상세 페이지의 th/td (지원대상·지원내용·신청절차 등) 가 모두 `body` 한 덩어리로 합쳐져서 적재되고, `IngestPolicyRequest` 가 받을 수 있는 풍부한 채널 (organization, supportContent, applyMethods, referenceSites 등) 이 모두 빈 상태.
2. **HTML 노이즈**: 본문에 `-->`, `\r`, 빈 td 잔존.
3. **관련 사이트 정보 미활용**: 상세 페이지의 "관련 사이트" / "신청 사이트" URL 안에 더 풍부한 안내 콘텐츠가 있으나 fetch 안 함.
4. **후속 단계 가시성 부족**: 정책 적재 후 enrichment·guide·rule·RAG indexing 5단계가 비동기로 진행되지만, 어느 단계가 실패했는지 한눈에 확인할 수단이 없음. 실제로 5건 모두 RAG indexing 단계가 silent fail 했고 (#126), 관측 부재로 발견까지 시간 소요.
5. **사용자 노출 게이트 없음**: 후속 단계 미완료 정책 (RAG 청크 0 등) 이 사용자에게 그대로 노출돼 적합도·Q&A 가 빈약한 결과를 반환.

### 목표

- **G1**: n8n youth-seoul-crawl 워크플로우가 plcyInfo 상세 페이지의 모든 th/td 를 백엔드 정규화 채널에 정확히 매핑.
- **G2**: n8n 이 관련/신청 사이트 URL 을 fetch 해서 LLM (gpt-4o-mini, 정책당 1회) 으로 정제 후 `enrichment` payload 조립.
- **G3**: 백엔드에 신규 테이블 `policy_processing_step` 으로 정책당 5단계 (INGESTION·ENRICHMENT·GUIDE·RULE·RAG_INDEXING) 진행 상태 추적.
- **G4**: 어드민 페이지에서 정책별 5단계 status·실패 사유·시간 한 화면에서 확인 + 수동 retry.
- **G5**: 사용자 페이지에서 후속 단계 미완료 정책에 "정확한 정보 수집 중..." 안내 노출 + LLM 기반 기능 (적합도·Q&A) 비활성화.
- **G6**: #126 회귀 방지 — listener 가 silent fail 하지 않고 step 테이블에 FAILED 가 기록되도록.

### 비목표 (다음 사이클로 분리)

- **#3 첨부파일 수집**: plcyInfo 에는 첨부 마크업이 없음. sprtInfo (`youth.seoul.go.kr/infoData/sprtInfo/...`) 별도 워크플로우 + image MIME 허용이 필요해 별도 spec.
- **#4 포스터 이미지 저장**: `policy.poster_storage_key` 컬럼 + 이미지 다운로드 분기 + 정책 상세 응답 DTO 변경이 필요해 별도 spec.
- **SPA 처리 (Browserless 등)**: 이번 사이클은 정적 fetch + SPA detection skip. SPA 비율 측정 후 다음 사이클에 도입 결정.
- **관련 사이트 LLM 결과 캐싱**: 첫 사이클 비용 측정 후 별도 도입.
- **자동 retry**: listener 단계 자동 retry 는 비용/시간 폭주 위험. 어드민 수동 retry 만 제공.

## 2. 접근 비교

### 2-1. enrichment LLM 처리 위치

| 측면 | A. n8n 안에서 처리 (선택) | B. 백엔드 안에서 처리 |
|---|---|---|
| LLM 호출 위치 | n8n Function 노드 → OpenAI HTTP Request | `EnrichmentService` 신규 도메인 서비스 |
| 외부 사이트 fetch | n8n HTTP Request 노드 | 백엔드 `RestClient` |
| 추적 정보 전달 | payload 에 status + skippedUrls 포함 | 백엔드가 직접 step 기록 |
| 신규 백엔드 코드 | 적음 (수신만 확장) | 큼 (서비스·port·외부 클라이언트 신규) |
| n8n 워크플로우 복잡도 | 큼 | 작음 |
| 다른 워크플로우 (복지로·온통청년) 재사용 | n8n function 공유 어려움 | EnrichmentService 가 통합 도구 |
| 호출 결과 비용 추적 | n8n 외 — `metrics` 모듈 통합 어려움 | `metrics` 모듈 자연 통합 |

**선택: A (n8n 처리)** — 사용자 의도 ("온통청년처럼 해당 사이트 내에서 llm으로 정보 추출") 와 일치. 비용 추적은 다음 사이클의 metrics 통합으로 보완.

### 2-2. 단계 추적 데이터 모델

| 측면 | A. 신규 테이블 `policy_processing_step` (선택) | B. `ingestion_run_log` 확장 | C. `policy.processing_status` JSONB |
|---|---|---|---|
| 정책당 행 수 | step × attempt | step × attempt | 1 (jsonb map) |
| 어드민 조회 SQL | `WHERE policy_id = ? ORDER BY step` | 동일 | jsonb 필터링 복잡 |
| 단계별 실패율 집계 | `GROUP BY step, status` 단순 | 동일 | jsonb 함수 필요 |
| 기존 의미 보존 | ingestion_run_log 그대로 둠 | 의미 변경, 마이그레이션 폭 큼 | 신규 컬럼 1개 |
| 시간 추적 정확도 | step 단위 IN_PROGRESS → SUCCESS 명확 | 동일 | row 1개에 모든 정보 압축 → 변경 추적 어려움 |

**선택: A (신규 테이블)** — 어드민 조회·집계·시간 추적 모두 SQL 단순. 기존 ingestion_run_log 의 의미 (수신 1회당 1행) 그대로 유지.

### 2-3. 사용자 노출 게이트

| 측면 | A. `policy.processing_status` 컬럼 (선택) | B. derived (policy_document chunks > 0) |
|---|---|---|
| 정책 조회 시 부담 | 단순 WHERE 절 + index 활용 | 매 조회마다 join 또는 EXISTS |
| 게이트 변경 시점 | listener 가 명시적 갱신 | RAG 청크 발생/삭제에 따라 자동 |
| 정확성 | listener 갱신 누락 시 stale | 항상 정확 |
| 노출 정책 변경 유연성 | enum 값 추가로 확장 | 게이트 조건 변경 시 모든 쿼리 수정 |

**선택: A (`processing_status` 컬럼)** — 정책 조회 핫패스라 쿼리 부담 최소화 우선. listener 갱신 누락 위험은 `markFinished` 헬퍼 1개로 통일.

## 3. 아키텍처

```
┌──────────────────────────────────────────────────────────────────────┐
│  n8n youth-seoul-crawl 워크플로우 (개편)                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                │
│  │ plcyInfo     │→ │ 참고/신청    │→ │ LLM 정제 1회  │                │
│  │ 상세 fetch + │  │ 사이트 fetch │  │ (gpt-4o-mini)│                │
│  │ th/td 매핑   │  │ + SPA 검출   │  │              │                │
│  └──────────────┘  └──────────────┘  └──────┬───────┘                │
│                                              ↓                        │
│                  ┌─────────────────────────────────────┐              │
│                  │ enrichment payload 조립             │              │
│                  │ - sections{...} / cleanedText      │              │
│                  │ - status: SUCCESS / SKIPPED(reason)│              │
│                  │ - skippedUrls[]                     │              │
│                  └─────────────────────────────────────┘              │
│                                              ↓                        │
│                                  POST /api/internal/ingestion/policies│
└──────────────────────────────────────────────┼───────────────────────┘
                                               ↓
┌──────────────────────────────────────────────────────────────────────┐
│  Backend                                                              │
│  ┌─────────────────────────────────────┐                              │
│  │ IngestionService.receivePolicy()   │ → step="INGESTION" 기록      │
│  │   + enrichment payload 저장          │ → step="ENRICHMENT" 기록    │
│  │   + PolicyUpsertedEvent 발행         │   (n8n status 그대로 복사)   │
│  └────────────────┬────────────────────┘                              │
│                   ↓ (async, llmExecutor)                              │
│  ┌──────────────────────────────────────┐                             │
│  │ 3개 listener (Guide/Rule/RAG)        │ → step="GUIDE" 기록         │
│  │ start → 작업 → success/fail           │ → step="RULE" 기록          │
│  │                                       │ → step="RAG_INDEXING" 기록   │
│  └────────────┬─────────────────────────┘                              │
│               ↓                                                        │
│  ┌──────────────────────────────────────┐                             │
│  │ policy.processing_status 갱신         │                             │
│  │ (INGESTION+RAG_INDEXING SUCCESS면     │                             │
│  │  READY, 그 외 PROCESSING)             │                             │
│  └──────────────────────────────────────┘                             │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│  Admin (백엔드 API + 프론트엔드 UI)                                    │
│  - GET /api/internal/admin/policies/{id}/processing-steps             │
│  - GET /api/internal/admin/processing-failures (필터/페이징)            │
│  - POST /api/internal/admin/policies/{id}/processing-steps/{step}/retry│
│  - 프론트엔드 대시보드: 정책별 5단계 진행 상황 한눈에                       │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│  사용자 페이지 (프론트엔드)                                             │
│  - PROCESSING 정책: "정확한 정보 수집 중..." 안내 워딩                    │
│  - READY 정책: 정상 노출                                                │
│  - PROCESSING 상태에서 적합도·Q&A 버튼 비활성화                          │
└──────────────────────────────────────────────────────────────────────┘
```

**책임 분리 요약**:

- **n8n**: 외부 사이트 fetch + LLM 정제 (정책당 1회) + enrichment payload 조립.
- **백엔드 ingestion**: 수신 + INGESTION/ENRICHMENT step 기록 + 이벤트 발행.
- **백엔드 listener**: 각자 책임지는 단계의 status 를 `policy_processing_step` 에 기록.
- **백엔드 admin API**: 단계별 진행 조회 + 수동 retry.
- **프론트엔드 admin**: 시각화 + 수동 조작.
- **프론트엔드 사용자**: 가용성 표시.

## 4. 데이터 흐름

### 4-1. n8n 워크플로우 (정책 1건 단위)

```
[1] 목록 페이지 fetch → goView('V...') 정규식으로 plcyBizId 추출
[2] (정책 1건) plcyInfo/view.do?plcyBizId=V... fetch
[3] th/td 파싱 → 구조화된 dict
    {
      title, category, organization, summary (정책 소개),
      supportTarget, supportContent, supportScale, businessPeriod*,
      applyStart, applyEnd, referenceSites[관련사이트 a 태그들],
      applyUrl (신청 사이트), screeningMethod, submissionDocuments,
      additionalQualification, participationRestriction,
      rawCodes(연령/학력/전공/취업상태/zipCodes)
    }
[4] 관련/신청 사이트 fetch (각 URL 마다)
    for each URL in [...referenceSites, applyUrl]:
      - 정적 fetch (timeout 5초, retry 1회)
      - SPA detection: 응답 < 2KB OR script-only 패턴이면 skip
      - 성공: HTML → text capping (6KB) 로 압축
      - 실패/skip: skipped_urls 에 {url, reason} 기록
[5] LLM 정제 (gpt-4o-mini, 1회)
    input: plcyInfo body + 성공한 외부 사이트 텍스트들
    output JSON:
      {
        cleanedText: "...",
        sections: {supportTarget, supportContent, applyMethod,
                   requiredDocuments, deadlineNote, policyOverview,
                   eligibilityCriteria, operatingOrganization, contactPhone}
      }
[6] enrichment payload 조립:
    enrichment: {
      sourceUrl: plcyInfo 상세 URL,
      fetchedAt: now,
      extractor: "youth-seoul-llm-v1",
      confidence: 0.85,
      status: "SUCCESS" or "SKIPPED",   ← LLM 단계 결과
      sections: {...},
      cleanedText: "...",
      skippedUrls: [{url, reason}]      ← NEW: 백엔드 추적용
    }
[7] POST /api/internal/ingestion/policies (전체 payload)
```

### 4-2. 백엔드 — step status 기록 시점

```
IngestionService.receivePolicy() 시작
  → policy_processing_step 에 INGESTION/IN_PROGRESS 기록
  ↓
  ... 정규화 / dedup / policy upsert ...
  ↓
  INGESTION/SUCCESS 또는 INGESTION/FAILED(reason) 마킹

  payload 의 enrichment 가 있으면:
    enrichment.status 그대로 복사해서
    ENRICHMENT/SUCCESS or ENRICHMENT/SKIPPED(reason=skippedUrls 직렬화) 기록

  PolicyUpsertedEvent 발행
  ↓ (async, llmExecutor)

GuideGenerationEventListener:
  → GUIDE/IN_PROGRESS 기록
  → 작업
  → GUIDE/SUCCESS or GUIDE/FAILED(reason) 마킹

EligibilityRuleGenerationEventListener:
  → RULE/IN_PROGRESS → ... → RULE/SUCCESS/FAILED

RagIndexingEventListener:
  → RAG_INDEXING/IN_PROGRESS → ... → RAG_INDEXING/SUCCESS/FAILED
```

### 4-3. `policy.processing_status` 갱신 로직

각 listener / IngestionService 가 `markFinished(step, status, reason)` 헬퍼를 호출할 때, **호출 시점의 트랜잭션 안에서** policy 의 derived 컬럼을 같이 갱신:

```
processing_status = READY 조건:
  - INGESTION = SUCCESS AND
  - RAG_INDEXING = SUCCESS
  - (GUIDE, RULE, ENRICHMENT 의 status 는 무관)

위 조건 미충족 시 = PROCESSING
```

**구현 노트**:

- `PolicyProcessingStepService.markFinished()` 가 단일 진실 소스. step row 갱신 + policy.processing_status 재계산을 한 트랜잭션 안에서 수행.
- listener 각자의 `@Transactional` 메서드 안에서 호출 → step row 갱신 실패 시 listener 작업도 rollback (정합성 보장).
- 재계산 쿼리: 해당 policy 의 latest attempt step 들을 한 번에 조회 → 위 READY 조건 평가 → policy.processing_status update.

**근거**: GUIDE / RULE 은 정책 노출의 필수 게이트가 아니라 부가 기능. RAG_INDEXING 까지 끝나야 적합도·Q&A 가 의미 있는 결과를 내므로 그 시점이 사용자 노출 게이트.

### 4-4. 실패 격리

- n8n LLM 실패 → enrichment 비워서 전송. ingestion 자체는 정상 진행.
- 백엔드 listener 실패 → 해당 step 만 FAILED, 다른 listener 는 독립 실행.
- **한 단계 실패가 다른 단계를 막지 않음.**

## 5. 데이터 모델

### 5-1. 신규 테이블 `policy_processing_step`

```sql
CREATE TABLE policy_processing_step (
    id                BIGSERIAL PRIMARY KEY,
    policy_id         BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    step              VARCHAR(20) NOT NULL,         -- enum 값
    status            VARCHAR(20) NOT NULL,         -- enum 값
    attempt           INTEGER NOT NULL DEFAULT 1,   -- 재실행 시 증가
    reason            VARCHAR(500),                 -- FAILED/SKIPPED 사유
    detail_json       JSONB,                        -- skippedUrls 등 부가 정보
    started_at        TIMESTAMP NOT NULL,
    finished_at       TIMESTAMP,                    -- IN_PROGRESS 동안 NULL
    duration_ms       INTEGER,                      -- finished_at - started_at
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),

    UNIQUE (policy_id, step, attempt)
);

CREATE INDEX idx_pps_policy_step ON policy_processing_step (policy_id, step);
CREATE INDEX idx_pps_status_finished ON policy_processing_step (status, finished_at DESC);
CREATE INDEX idx_pps_step_status ON policy_processing_step (step, status);
```

### 5-2. enum 정의 (도메인)

```java
package com.youthfit.policy.domain.model;

public enum ProcessingStep {
    INGESTION,      // n8n payload 수신·정규화·적재
    ENRICHMENT,     // n8n 측 LLM 정제 결과 (status 만 백엔드가 복사)
    GUIDE,          // GuideGenerationEventListener
    RULE,           // EligibilityRuleGenerationEventListener
    RAG_INDEXING    // RagIndexingEventListener
}

public enum ProcessingStatus {
    IN_PROGRESS,    // 시작했지만 미완료
    SUCCESS,        // 정상 완료
    FAILED,         // 예외/타임아웃
    SKIPPED         // 의도적 skip (SPA, 중복, cost-guard 등)
}
```

### 5-3. `policy` 테이블 컬럼 추가

```sql
ALTER TABLE policy ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING';
-- READY | PROCESSING
CREATE INDEX idx_policy_processing_status ON policy (processing_status);
```

```java
package com.youthfit.policy.domain.model;

public enum PolicyAvailability {
    PROCESSING,  // 후속 단계 진행 중 — 사용자에게 "수집 중" 워딩
    READY        // 모든 단계 SUCCESS/SKIPPED, 사용자 노출 가능
}
```

### 5-4. 마이그레이션

- `V20260601__create_policy_processing_step.sql`
- `V20260602__add_policy_processing_status.sql` (DEFAULT 'PROCESSING')
- `V20260603__backfill_policy_processing_status.sql`
  - 기존 정책 중 `policy_document` chunk > 0 인 정책은 READY 로 backfill
  - 나머지는 PROCESSING 유지 (어드민에서 수동 재처리 대상)

### 5-5. `IngestPolicyRequest.EnrichmentPayload` 확장

```java
public record EnrichmentPayload(
    String sourceUrl,
    LocalDateTime fetchedAt,
    String extractor,
    Double confidence,
    String status,                          // 기존
    EnrichmentSectionsPayload sections,
    List<ExtraAttachmentPayload> extraAttachments,
    String cleanedText,
    List<SkippedUrlPayload> skippedUrls    // ★ NEW: 추적용
) {}

public record SkippedUrlPayload(
    String url,
    String reason  // "SPA_DETECTED" / "TIMEOUT" / "HTTP_ERROR" / ...
) {}
```

## 6. 에러 처리

### 6-1. 실패 시나리오 매트릭스

| 시점 | 실패 종류 | 동작 | step 기록 | 정책 상태 |
|------|-----------|------|-----------|-----------|
| n8n: plcyInfo fetch | timeout / 5xx | 워크플로우 retry 3회 후 skip | (없음 — 백엔드 도달 안 함) | (영향 없음) |
| n8n: 관련 사이트 fetch | SPA 검출 | `skippedUrls` 에 `reason="SPA_DETECTED"`, 계속 진행 | `ENRICHMENT/SUCCESS` (다른 사이트 정제 성공) 또는 `ENRICHMENT/SKIPPED` (전부 실패) | PROCESSING → READY 가능 |
| n8n: 관련 사이트 fetch | 타임아웃/HTTP 에러 | `skippedUrls` 에 `reason="TIMEOUT"/"HTTP_4XX"`, 계속 진행 | 위와 동일 | 동일 |
| n8n: LLM 호출 | API 실패 | `enrichment.status="SKIPPED"`, `reason="LLM_API_ERROR"` | `ENRICHMENT/SKIPPED` | PROCESSING → READY 가능 |
| 백엔드: ingestion 정규화 | dedup (SKIPPED_DUPLICATE) | 기존 동작 유지 | `INGESTION/SKIPPED(reason="DUPLICATE")` | (변경 없음) |
| 백엔드: ingestion 정규화 | 예외 | 기존 `ingestion_item_failure` 기록 + 정책 미적재 | `INGESTION/FAILED(reason=예외요약)` | (정책 없음) |
| 백엔드: GUIDE listener | LLM 실패 | catch swallow (기존 로직) | `GUIDE/FAILED(reason)` | 다른 단계 영향 없음 → READY 가능 |
| 백엔드: RULE listener | LLM 실패 | 동일 | `RULE/FAILED(reason)` | 동일 |
| 백엔드: RAG_INDEXING listener | embedding 실패 | catch swallow | `RAG_INDEXING/FAILED(reason)` | PROCESSING 유지 (사용자 노출 X) |

### 6-2. Retry 정책

- **n8n**: 정적 fetch 는 5초 timeout, 1회 재시도 (n8n HTTP Request 노드의 retry-on-fail 옵션).
- **백엔드 listener**: 자동 retry 안 함. FAILED 상태 유지하고 어드민에서 수동 재실행:
  ```
  POST /api/internal/admin/policies/{id}/processing-steps/{step}/retry
  ```
- **이유**: 자동 retry 는 비용/시간 폭주 위험. 어드민에서 의도적 재시도가 안전. 수동 retry 는 새 `attempt` 행으로 추가 (기존 행은 history 로 유지).

### 6-3. 어드민 가시성

어드민 대시보드의 핵심 필터:

- `processing_status = PROCESSING` 인 정책 (대기 중)
- `RAG_INDEXING = FAILED` 인 정책 (사용자 노출 안 됨, 긴급)
- `GUIDE = FAILED` / `RULE = FAILED` (부가 기능 누락, 우선순위 낮음)
- `ENRICHMENT.skippedUrls` 비지 않은 정책 (n8n 단계 데이터 품질 이슈)

각 행 클릭 → 5단계 step 상세 표 + `reason` + `detail_json` 보임.

### 6-4. 사용자 페이지 안내 워딩

- **정책 카드**: PROCESSING 정책은 카드 우상단에 작은 뱃지 "정보 수집 중" (회색).
- **정책 상세 페이지**: 본문 상단에 안내 박스
  ```
  ⓘ 더 정확한 정보를 수집 중입니다.
    빠른 시일 안에 자격 진단·AI 가이드·Q&A 기능까지
    완성된 형태로 제공해 드릴게요.
  ```
- PROCESSING 정책 상세에서 LLM 기반 기능 (적합도 판정, Q&A) 은 **비활성화** (버튼 disable + 안내).

## 7. 테스트 전략

| 영역 | 테스트 종류 | 핵심 케이스 |
|------|-------------|-------------|
| **n8n 워크플로우** | 수동 실행 + fixtures | (a) plcyInfo 정상 + 관련 사이트 2개 정상 → enrichment SUCCESS / (b) 관련 사이트 1개 SPA + 1개 정상 → enrichment SUCCESS + skippedUrls 1개 / (c) 모든 관련 사이트 SPA → enrichment SKIPPED |
| **IngestionService 단위** | JUnit + 슬라이스 | enrichment payload 가 있을 때 `ENRICHMENT/SUCCESS` step 기록 / 없을 때 step 미기록 / SKIPPED 전달 시 `ENRICHMENT/SKIPPED` 기록 |
| **listener 통합** | JUnit + `@SpringBootTest` | Guide/Rule/RAG listener 각각이 success/fail 시 step 정확히 기록 / `processing_status` 가 RAG_INDEXING 완료 시점에 READY 로 바뀌는지 |
| **PolicyProcessingStepRepository** | JUnit | `findLatestByPolicyAndStep` / `markStarted` / `markFinished(SUCCESS/FAILED, reason)` 의 unique 제약 (policy_id, step, attempt) 동작 |
| **admin API** | MockMvc | `GET /admin/policies/{id}/processing-steps` 응답 구조 / `GET /admin/processing-failures` 필터 (step, status) / retry endpoint 가 새 attempt 행 추가하는지 |
| **policy 사용자 API** | MockMvc | `processing_status` 응답 노출 / PROCESSING 정책의 LLM 기능 endpoint 차단 |
| **frontend 어드민** | Vitest + Testing Library | 5단계 step 표 렌더 / status 별 색상 / retry 버튼 클릭 동작 |
| **frontend 사용자** | Vitest + Testing Library | PROCESSING 카드 뱃지 표시 / 상세 안내 박스 / 적합도 버튼 비활성 |

### 7-1. 회귀 방지 테스트 (#126 재발 방지)

```java
@Test
void RAG_indexing_listener_가_PolicyUpsertedEvent_에_정상_반응한다() {
    // given: 정책 1건 적재
    Long policyId = ingestionService.receivePolicy(samplePayload);

    // when: 이벤트 발행 후 대기
    waitFor(() -> ragIndexedFor(policyId), Duration.ofSeconds(5));

    // then: policy_document chunks > 0 + RAG_INDEXING/SUCCESS 기록
    assertThat(policyDocumentRepository.countByPolicyId(policyId)).isGreaterThan(0);
    assertThat(stepRepository.findLatest(policyId, RAG_INDEXING).getStatus())
        .isEqualTo(SUCCESS);
}
```

## 8. 성공 기준

- 청년몽땅 정책 1건 ingest 시 `policy_processing_step` 에 INGESTION + ENRICHMENT + GUIDE + RULE + RAG_INDEXING 5행 기록됨.
- `processing_status` 가 RAG_INDEXING SUCCESS 시점에 READY 로 자동 전환.
- 어드민 페이지에서 정책별 5단계 진행 한 화면에서 확인 + 실패 사유 / 시간 모두 노출.
- 사용자 페이지에서 PROCESSING 정책은 "정보 수집 중" 안내 + LLM 기능 disable.
- n8n 으로 전체 1회 풀 크롤 시 신규/변경 정책 ~10건 enrichment payload 완비 상태로 적재 (수동 검증).

## 9. Phase 분할 (plan 단계로 인계)

본 spec 의 변경 범위가 백엔드 5개 모듈 + 프론트엔드 2개 영역 + n8n 1개에 걸쳐 있어, 단일 PR 로 묶기보다는 phase 별 분할 후 독립 PR 권장:

- **Phase A**: 백엔드 추적 인프라 (`policy_processing_step` 테이블 + enum + repository + listener 측 기록 + #126 회귀 테스트). 사용자 UX 영향 없음.
- **Phase B**: `policy.processing_status` 컬럼 + 사용자용 정책 API 응답 노출 + LLM 기반 기능 차단 + 마이그레이션 backfill.
- **Phase C**: 프론트엔드 사용자 페이지 — 정책 카드 뱃지 + 상세 안내 박스 + 적합도/Q&A 버튼 disable.
- **Phase D**: n8n youth-seoul-crawl 워크플로우 개편 (th/td 풍부 매핑 + 관련 사이트 fetch + LLM 정제 + enrichment payload 조립).
- **Phase E**: 백엔드 admin API (`/admin/policies/{id}/processing-steps`, `/admin/processing-failures`, retry) + 프론트엔드 admin 대시보드 UI.

Phase A → B → C → D → E 순서로 진행. 각 phase 끝에 PR 단위로 머지.
