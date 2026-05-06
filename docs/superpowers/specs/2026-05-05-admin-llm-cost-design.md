# 어드민 — Spec 4: LLM 비용 대시보드 설계

> **상태**: Spec 확정 (2026-05-06 brainstorming 완료)
> **작성일**: 2026-05-05 (파일명 유지)
> **시리즈**: 어드민 시리즈 5개 중 #4
> **선행**: Spec 1 (admin foundation) DONE, Spec 2 (admin email tracking) DONE, Spec 3 (admin qna cache log) DONE

---

## 1. 목표

LLM/임베딩 호출 비용을 운영적으로 추적해 비용 방어 장치를 갖춘다 (CLAUDE.md "비용 방어 장치" 명시 원칙).
- **집계**: 시간별·일자별·모듈별 호출 수 / 토큰 수 / 추정 비용 (USD 적재 + KRW 환산 표시)
- **건별 적재 없음**: 호출 수가 많아 건별 row는 운영 부담. 1시간 버킷 사전 집계로 적재량 통제.
- **추세 지표**: 비용 급증·이상치를 추세에서 발견하기 위한 1차 데이터 소스.

## 2. 범위

### In
- `LlmCostBucket` 엔티티 (신규 `metrics` 모듈) + 1시간 단위 upsert 적재
- 5개 OpenAI 클라이언트(`qna`, `guide`, `rag`, `ingestion`, `eligibility`) → `LlmCallRecorded` ApplicationEvent 발행 (호출 직후 동기 publish)
- 비동기 listener (`@Async + @EventListener`) → `LlmCostBucket` upsert
- 모델별 가격표 — 정적 enum (`LlmModelPricing`) 기반 토큰 → USD 변환
- 어드민 화면:
  - KPI 4개 (오늘 비용 / 이번주 비용 / 이번달 비용 / 이번달 호출 수)
  - 라인 차트 — 시간별 비용 추이 (24h / 7D / 30D 토글)
  - Stacked bar — 일자별 모듈 분포 (QNA / GUIDE / EMBEDDING / OTHER)
  - 모델별 합계 테이블 (호출 수, 토큰, 비용)
- 보관 정책 — 시간 버킷 90일 / 일별 롤업 후 무기한
- Spec 3 비용 절감 산식과 통합 (Spec 3 § 14 "후속" 항목)

### Out
- 사용자별 비용 추적 (개인정보 부담 + 분석 가치 낮음)
- 실시간 알림 (비용 급증) — 외부 모니터링 (Grafana 등) 책임
- 비용 한도 설정 / 자동 차단 — 별도 v1 spec
- 환율 외부 API 동기화 — 정적 상수로 시작
- OpenAI 외 다른 LLM 제공자 (Anthropic 등) — v0 미사용
- 호출 1건 단위 상세 디버깅 화면 (Spec 3 cache lookup 디버깅과 다름 — LLM 호출 양이 압도적으로 많음)

## 3. 핵심 결정 로그

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| 1 | 모듈 위치 | 신규 `metrics` 모듈 | "비용 측정"은 어드민 조회와 별도 도메인. admin 모듈은 ReadModel만 (Spec 1~3 패턴 일관성). admin 비대화 방지 |
| 2 | OpenAI 통합 패턴 | **ApplicationEvent 발행** — 5개 클라이언트가 호출 직후 `LlmCallRecorded` publish, `metrics` 모듈 listener가 buckets upsert | 클라이언트 → metrics 단방향 의존 (역방향 X). 공통 facade 도입 시 5개 시그니처 통합 비용 큼. AOP 침투적·디버깅 어려움 |
| 3 | 적재 방식 | `@Async + @EventListener` 비동기 + 1시간 버킷 upsert | 호출 hot path 영향 최소. Spec 3 `QnaCacheLookupEventListener` 패턴 답습 |
| 4 | 버킷 단위 | **1시간** (UTC truncate) | 차트 해상도 충분, 적재량 5분 대비 1/12. (모델 수 5 × 모듈 수 4) × 24h × 30일 ≈ 14,400 row/월 |
| 5 | 가격표 보관 | 정적 enum (`LlmModelPricing`) | 가격 변경 빈도 낮음 → 코드 PR로 충분. DB 엔티티는 운영 부담 vs 변경 빈도 비례 안 맞음. 과거 데이터 소급 갱신 필요성 X |
| 6 | 통화 | USD 적재, frontend에서 정적 환율 상수로 KRW 표시 | 환율 동기화 인프라 over-engineering. 환율은 `application.yml` 상수, 분기별 갱신만 |
| 7 | 데이터 모델 | `(bucket_at, module, model)` UNIQUE → upsert | row 폭증 방지. 동시성은 `ON CONFLICT ... DO UPDATE` 또는 application-level lock |
| 8 | 보관 정책 | 시간 버킷 90일 → 일별 롤업 무기한 | 트렌드 분석 가치는 일별로 충분. 90일 이후 시간 해상도 불필요 |
| 9 | 토큰 추출 | OpenAI `response.usage.{prompt_tokens, completion_tokens, total_tokens}` 필드 파싱 추가 | 5개 클라이언트가 현재 usage 미추적. Spec 4 구현 시 모두 추출 + 이벤트 payload 포함 |
| 10 | 임베딩 비용 | `prompt_tokens` = input tokens, `completion_tokens` = 0 | OpenAI embeddings API는 input만 과금. 일관 컬럼 사용 |

## 4. 데이터 모델

### 4.1 엔티티 — `metrics/domain/model/LlmCostBucket`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | `BIGINT PK` | auto |
| `bucket_at` | `TIMESTAMP` | UTC, 시간 단위 truncate (`date_trunc('hour', now())`) |
| `module` | `VARCHAR(20)` enum | `QNA` \| `GUIDE` \| `EMBEDDING` \| `INGESTION` \| `ELIGIBILITY` |
| `model` | `VARCHAR(60)` | OpenAI 모델명 (예: `gpt-4o-mini`, `text-embedding-3-small`) |
| `call_count` | `INT` | 누적 호출 수 |
| `prompt_tokens` | `BIGINT` | 누적 input tokens |
| `completion_tokens` | `BIGINT` | 누적 output tokens (임베딩은 0) |
| `total_tokens` | `BIGINT` | `prompt + completion` (편의 컬럼) |
| `estimated_cost_usd` | `DECIMAL(12,6)` | 누적 추정 비용 (USD) |
| `created_at` | `TIMESTAMP` | 첫 적재 시각 |
| `updated_at` | `TIMESTAMP` | 마지막 upsert 시각 |

UNIQUE 제약: `(bucket_at, module, model)`

### 4.2 인덱스
- `idx_llm_cost_bucket_at` on `(bucket_at DESC)`
- `idx_llm_cost_bucket_module_at` on `(module, bucket_at DESC)`
- (UNIQUE 제약이 자동 인덱스 — 별도 idx 불필요)

### 4.3 모듈 enum — `metrics/domain/model/LlmModule`

```java
public enum LlmModule {
  QNA,         // qna 모듈 LLM 호출 (답변 생성)
  GUIDE,       // guide 모듈 LLM 호출 (정책 가이드 생성)
  EMBEDDING,   // rag 모듈 임베딩 호출 (모든 임베딩 통합)
  INGESTION,   // ingestion 모듈 LLM 호출 (정책 기간 추출)
  ELIGIBILITY  // eligibility 모듈 LLM 호출 (규칙 추출)
}
```

> 임베딩은 `EMBEDDING`으로 통합 (rag·notification 등에서 호출하더라도 단일 모듈로 집계). 분석 시 모델명(`text-embedding-3-*`)으로 세분 가능.

### 4.4 가격표 — `metrics/domain/model/LlmModelPricing`

```java
public enum LlmModelPricing {
  GPT_4O_MINI("gpt-4o-mini", new BigDecimal("0.000150"), new BigDecimal("0.000600")),
  GPT_4O("gpt-4o", new BigDecimal("0.0025"), new BigDecimal("0.010")),
  TEXT_EMBEDDING_3_SMALL("text-embedding-3-small", new BigDecimal("0.00002"), BigDecimal.ZERO),
  TEXT_EMBEDDING_3_LARGE("text-embedding-3-large", new BigDecimal("0.00013"), BigDecimal.ZERO);

  // 단위: USD per 1K tokens
  private final String modelId;
  private final BigDecimal inputPer1K;
  private final BigDecimal outputPer1K;
}
```

- 미등록 모델은 `UNKNOWN` 처리 (cost 0, model 컬럼은 raw 값 보존). plan 단계에서 fallback 정책 명시.
- 가격 변경 시 enum 갱신 PR 1개. 과거 적재 데이터는 그대로(소급 X).

### 4.5 이벤트 — `metrics/application/event/LlmCallRecorded`

```java
public record LlmCallRecorded(
    LlmModule module,
    String model,
    int promptTokens,
    int completionTokens,
    Instant calledAt
) {}
```

- 모든 OpenAI 클라이언트가 호출 직후 `applicationEventPublisher.publishEvent(new LlmCallRecorded(...))`
- payload는 minimal — userId / questionText 등 컨텍스트 정보는 비범위(개인정보 + 적재량)

### 4.6 마이그레이션

신규 SQL 파일 1개: `2026-05-05-llm-cost-bucket.sql`. 백필 없음 — 과거 호출은 추적 불가, 적재 시작 시점부터 누적.

## 5. 아키텍처 & 적재 흐름

### 5.1 모듈 경계

| 위치 | 책임 |
|---|---|
| `metrics/domain/model/LlmCostBucket` | 엔티티 |
| `metrics/domain/model/LlmModule` | 모듈 enum |
| `metrics/domain/model/LlmModelPricing` | 가격 enum + 토큰 → USD 계산 메서드 |
| `metrics/infrastructure/repository/LlmCostBucketRepository` | JPA repo + upsert 쿼리 (`@Modifying` native or JPQL) |
| `metrics/application/event/LlmCallRecorded` | record 이벤트 |
| `metrics/application/event/LlmCallRecordedListener` | `@Async + @EventListener` 비동기 적재 |
| `metrics/application/service/LlmCostBucketService` | bucket upsert 도메인 서비스 |
| `qna/infrastructure/external/OpenAiQnaClient` | 호출 직후 `LlmCallRecorded` publish |
| `guide/infrastructure/external/OpenAiChatClient` | 위와 동일 |
| `rag/infrastructure/external/OpenAiEmbeddingClient` | 위와 동일 |
| `ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor` | 위와 동일 |
| `eligibility/infrastructure/external/OpenAiEligibilityRuleClient` | 위와 동일 |
| `admin/presentation/controller/AdminLlmCostController` | 조회 전용 컨트롤러 |
| `admin/presentation/dto/response/LlmCost*Response` | 어드민 응답 DTO |
| `admin/application/service/AdminLlmCostService` | 조회 + 집계 |
| `metrics/infrastructure/scheduler/LlmCostBucketRollupScheduler` | 90일 → 일별 롤업 (선택 — plan에서 결정) |

### 5.2 적재 흐름

```
OpenAi*Client.call(request)
  └─ HTTP 호출 → response with usage
  └─ applicationEventPublisher.publishEvent(LlmCallRecorded { module, model, tokens, calledAt })
       └─ (호출 트랜잭션과 무관, 동기 publish)
       └─ Listener (@Async) → LlmCostBucketService.recordCall(...)
            └─ bucketAt = truncateToHour(calledAt)
            └─ pricing = LlmModelPricing.of(model)
            └─ cost = pricing.calculate(promptTokens, completionTokens)
            └─ repository.upsert(bucketAt, module, model, +1, +tokens, +cost)
                 └─ ON CONFLICT (bucket_at, module, model) DO UPDATE
            └─ 적재 실패 시 try/catch + warn 로그 (호출 경로 영향 없음)
```

### 5.3 동시성

- 같은 (bucket, module, model) 조합 동시 upsert 가능
- PostgreSQL `INSERT ... ON CONFLICT (bucket_at, module, model) DO UPDATE SET ...` 으로 atomic 처리
- 또는 `@Lock(PESSIMISTIC_WRITE) + select-then-update` 패턴 — plan 단계에서 성능 측정 후 결정

### 5.4 트랜잭션 컨텍스트

- `LlmCallRecorded` publish는 호출 트랜잭션과 *분리*. 호출은 LLM API 호출이라 트랜잭션 의미 없음.
- 단순 `@EventListener + @Async` (Spec 3과 동일 패턴) — `@TransactionalEventListener` 불필요.

## 6. 어드민 화면

### 6.1 라우트
- `/admin/llm-cost` — 메인 (상세 화면 없음, lookup 단위 디버깅 X)

### 6.2 메인 화면 구성

**KPI 카드 4개** (Spec 2 `KpiCard` 재사용)
- 오늘 비용 (USD + KRW 환산)
- 이번주 비용 (월~일, KST 기준)
- 이번달 비용 (1일~말일)
- 이번달 호출 수 (전월 대비 트렌드 ↑↓)

**기간 토글** — 24h / 7D / 30D 라디오. 차트 두 개 모두 같은 기간 사용.

**라인 차트 — 시간별 비용 추이** (Recharts `LineChart`)
- X축: 시각 (24h: 시간 단위 / 7D, 30D: 일 단위)
- Y축: USD
- 모듈별 line 5개 (색상 구분)
- 7D / 30D 모드는 시간 버킷을 일별 합산

**Stacked bar — 일자별 모듈 분포** (Spec 2 `StackedBarChart` 재사용)
- 7D 또는 30D 윈도우 일별 적층
- 모듈 색상은 라인 차트와 동일

**모델별 합계 테이블**
- 컬럼: 모델명 / 호출 수 / 입력 토큰 / 출력 토큰 / 총 토큰 / 비용 (USD) / 비용 비중 (%)
- 정렬: 비용 내림차순

### 6.3 API 엔드포인트

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/v1/admin/llm-cost/kpi` | `LlmCostKpiResponse` |
| GET | `/api/v1/admin/llm-cost/series?range=24h\|7d\|30d` | `LlmCostSeriesResponse` (시간별 + 모듈별 시계열) |
| GET | `/api/v1/admin/llm-cost/by-module?range=7d\|30d` | `List<LlmCostModuleDailyResponse>` (stacked bar용) |
| GET | `/api/v1/admin/llm-cost/by-model?range=7d\|30d` | `List<LlmCostModelSummaryResponse>` (테이블용) |

인증: Spec 1의 `@RequireAdmin` 적용.

### 6.4 응답 DTO 명명 (Spec 2~3 패턴 답습)

- `LlmCostKpiResponse`
- `LlmCostSeriesResponse` (`{ range: string, points: List<{ at: Instant, costByModule: Map<LlmModule, BigDecimal> }> }`)
- `LlmCostModuleDailyResponse` (`{ date: LocalDate, module: LlmModule, totalCostUsd: BigDecimal, callCount: int }`)
- `LlmCostModelSummaryResponse` (`{ model: String, callCount: int, promptTokens: long, completionTokens: long, totalTokens: long, totalCostUsd: BigDecimal, costShare: BigDecimal }`)

### 6.5 프론트 파일

- `frontend/src/pages/admin/AdminLlmCostPage.tsx`
- `frontend/src/apis/admin.llmCost.api.ts`
- `frontend/src/components/charts/LineChart.tsx` (신규 — 재사용 가능 형태)
- `frontend/src/components/admin/llmCost/*` (KPI, 모델 테이블 등 페이지 한정 컴포넌트)

### 6.6 사이드바

`frontend/src/components/layout/AdminSidebar.tsx`에 `/admin/llm-cost` 항목 추가 (`soon: true` 없이 활성화).

## 7. 환경 변수 / 설정

| 키 | 기본값 | 의미 |
|---|---|---|
| `youthfit.metrics.llm-cost.bucket-retention-days` | `90` | 시간 버킷 보관일 (이후 일별 롤업 또는 단순 삭제) |
| `youthfit.metrics.llm-cost.usd-to-krw` | `1350` | KRW 환산 정적 환율 (분기별 운영자 갱신) |
| `youthfit.metrics.llm-cost.async-pool.core-size` | `2` | listener `@Async` 풀 코어 수 (선택 — 기존 풀 재사용 검토) |

> 기존 `@Async` 풀 (Spec 3에서 정립된 것)이 있으면 재사용. plan 단계에서 확인 후 결정.

## 8. 보관 정책 / 운영

- `llm_cost_bucket` 시간 버킷: 90일
- 90일 이후 처리: **plan 단계에서 결정** — (a) 단순 삭제 + 일별 롤업 테이블 신설, (b) 시간 버킷을 그대로 둘지. v0는 (a) 권장하되 운영 데이터 적재량 보고 결정.
- Listener `try/catch + warn 로그` — 적재 실패가 LLM 호출 경로에 영향 없음
- 별도 `operations/*-runbook.md` 작성하지 않음 (외부 의존 없음). plan "후속/미결" 섹션에 운영 메모 (가격표 갱신 절차, 환율 갱신 절차).

## 9. 테스트 전략

### 9.1 백엔드 단위
- `LlmModelPricing.calculate(promptTokens, completionTokens)` — 등록 모델 / UNKNOWN 모델 / 0 토큰 경계
- `LlmCostBucketService.recordCall(...)` — 신규 bucket 생성 / 기존 bucket 누적 / `bucket_at` 시간 truncate 정확성
- 모듈 enum 매핑 — 5개 클라이언트가 올바른 `LlmModule` 발행

### 9.2 백엔드 슬라이스 / 통합
- `LlmCallRecordedListener` — `@SpringBootTest` + 이벤트 발행 → repository 적재 검증, 적재 실패 시 호출 경로 영향 없음
- 5개 OpenAI 클라이언트 단위 테스트 — `usage` 파싱 + 이벤트 발행 1건 검증 (mock OpenAI response)
- `LlmCostBucketRepository.upsert` — Testcontainers (Postgres) ON CONFLICT 동작 검증, 동시 upsert 100건 시 race condition 없음
- `AdminLlmCostController` 슬라이스 (`@WebMvcTest + @WithMockUser(roles="ADMIN")`) — 4개 엔드포인트 200, range 파라미터 검증, 비ADMIN 401/403

### 9.3 백엔드 E2E
- 5개 모듈 LLM 호출 → bucket 1건 적재 검증 (Spec 3 `QnaCacheLookupLog` 통합 테스트 구조 답습)
- 같은 시간대 5번 호출 → bucket 1행, call_count=5 검증

### 9.4 프론트엔드
- 컴포넌트: KPI 카드 (USD/KRW 동시 표기), 라인 차트 (5개 모듈 line, 빈 데이터 / 1일 / 30일 풀), stacked bar, 모델 테이블 정렬
- 페이지 통합: range 토글 → API 재호출, 환율 정적 상수로 KRW 표시, 빈 응답 시 placeholder
- 비ADMIN 진입 시 redirect (Spec 1 `@RequireAdmin` 동등)

### 9.5 커버리지
- 신규 백엔드 코드 라인 커버리지 80%
- 프론트는 페이지·컴포넌트 단위 happy path + 에러 상태 1건씩

### 9.6 검증 커맨드 (plan 단계에서 정확한 명령어 명시)
- `./gradlew test`
- `cd frontend && npm run test`
- `cd frontend && npm run typecheck && npm run lint`

## 10. 의존성

- Spec 1 (admin foundation) DONE — 사이드바, `@RequireAdmin`, `/api/v1/admin/**` 라우트
- Spec 2 (admin email tracking) DONE — Recharts, `KpiCard`, `StackedBarChart`, 응답 DTO 명명 규칙
- Spec 3 (admin qna cache log) DONE — `@Async + @EventListener` 패턴 (`QnaCacheLookupEventListener` 답습)
- 기존 5개 OpenAI 클라이언트 — 본 spec에서 모두 수정
- Spec 5 (Ingestion 헬스)와 무의존 (병렬 진행 가능)

## 11. 변경 영향 범위

### 11.1 신규
- `metrics/` 모듈 전체 (domain/application/infrastructure/presentation 없음 — admin이 presentation)
  - `metrics/domain/model/LlmCostBucket`, `LlmModule`, `LlmModelPricing`
  - `metrics/domain/service/LlmCostCalculator` (가격 enum 위에 토큰 → USD 변환)
  - `metrics/infrastructure/repository/LlmCostBucketRepository`
  - `metrics/application/event/LlmCallRecorded`, `LlmCallRecordedListener`
  - `metrics/application/service/LlmCostBucketService`
  - `metrics/application/service/LlmCostQueryService` (admin 조회용 — admin 모듈에서 사용)
- `admin/presentation/controller/AdminLlmCostController`, `AdminLlmCostApi`
- `admin/application/service/AdminLlmCostService`
- `admin/presentation/dto/response/LlmCost{Kpi,Series,ModuleDaily,ModelSummary}Response`
- SQL 마이그레이션: `2026-05-05-llm-cost-bucket.sql`
- 프론트 페이지/컴포넌트/API 함수 (§ 6.5)

### 11.2 수정
- 5개 OpenAI 클라이언트 — 호출 직후 `LlmCallRecorded` publish + usage 파싱
  - `qna/infrastructure/external/OpenAiQnaClient`
  - `guide/infrastructure/external/OpenAiChatClient`
  - `rag/infrastructure/external/OpenAiEmbeddingClient`
  - `ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor`
  - `eligibility/infrastructure/external/OpenAiEligibilityRuleClient`
- `frontend/src/components/layout/AdminSidebar.tsx` — `/admin/llm-cost` 항목 추가
- `application.yml` (또는 동등) — 신규 설정 키 2개 (환율, retention)
- `App.tsx` 또는 라우터 — `/admin/llm-cost` 라우트 추가

## 12. 위험 / 트레이드오프

| 위험 | 완화 |
|---|---|
| 5개 클라이언트 동시 수정 → 회귀 위험 | 단위 테스트 (mock OpenAI usage) + 통합 테스트로 각 클라이언트 회귀 방지. 5개를 한 PR에서 처리하되 클라이언트별 커밋 분할 |
| `usage` 필드 누락 응답 (스트리밍·예외) | `usage` null 시 0으로 처리 + warn 로그. 비용은 0, 호출 수만 카운트 |
| Bucket UNIQUE race condition | PostgreSQL `ON CONFLICT` upsert로 atomic 처리. 통합 테스트로 동시성 100건 검증 |
| 가격표 부정확 (모델 갱신 누락) | UNKNOWN 모델은 cost 0 + warn. 운영 메모에 갱신 절차 명시. Grafana 등 외부에서 UNKNOWN 비율 모니터 (v1) |
| 환율 부정확 (동기화 안 함) | 환산은 frontend 표시용. 적재 데이터는 USD 그대로. 분기별 운영자 갱신 |
| 적재량 폭증 (모델/모듈 조합 폭발) | 5 모듈 × 5 모델 × 24h × 30일 ≈ 18,000 row/월 — 부담 적음. 모니터링 후 90일 retention 조정 |
| `@Async` listener 적재 실패 누락 | Spec 3과 동일 — 추세 지표라 일부 누락 허용. 적재 실패 metric은 Grafana에서 처리 (v1) |

## 13. 후속 / 비범위

- Spec 3 `youthfit.qna.cache.estimated-savings-per-hit-usd` 정적값 → 동적 산식 (실제 LLM 호출 평균 비용 기반)으로 마이그레이션
- 사용자별 비용 — 개인정보 정책 + 분석 가치 검토 후 v1
- 비용 한도 / 자동 차단 — 별도 v1 spec
- 환율 외부 API 동기화 (예: ExchangeRate-API)
- Anthropic 등 다른 LLM 제공자 추가 — `LlmModule`/`LlmModelPricing` 확장만 필요
- Micrometer 메트릭 노출 (시간당 비용 게이지 등) — Grafana 알림용
- 호출 1건 단위 디버깅 화면 — 적재량 vs ROI 검토

---

## 부록: 시리즈 5개 spec 간 공통 사항

| 항목 | 결정 메모 |
|---|---|
| 인증/라우팅 | Spec 1 결정 (`/api/v1/admin/**`, `@RequireAdmin`) |
| ReadModel 패턴 | admin 모듈은 조회만; 데이터는 각 도메인이 적재 (본 spec은 신규 metrics 모듈 적재) |
| 차트 라이브러리 | **Recharts** (Spec 2 결정, 본 spec 답습 — `LineChart` 신규) |
| 보관 정책 | 본 spec 시간 버킷 90일 (§ 8) |
| 디자인 토큰 | Spec 1 다크 사이드바 + 브랜드 indigo (`frontend/src/index.css`) |
| 비동기 적재 | Spec 3 `@Async + @EventListener` 패턴 답습 |
