# 어드민 LLM 비용 대시보드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 5개 OpenAI 클라이언트(`qna`/`guide`/`rag`/`ingestion`/`eligibility`)의 호출별 토큰·모델·모듈 정보를 ApplicationEvent 로 emit, 신규 `metrics` 모듈이 1시간 버킷으로 upsert 적재. 어드민에서 시간별 비용 추세·모듈별 분포·모델별 합계 KPI 를 조회한다.

**Architecture:**
- 신규 `metrics` 모듈: `LlmCostBucket` 엔티티 + `LlmModelPricing` 정적 가격 enum + `LlmCallRecorded` 이벤트 + `@Async @EventListener` 적재
- 5개 OpenAI 클라이언트: 응답 `usage` 필드 파싱 + `applicationEventPublisher.publishEvent(...)` 호출 (Q&A 스트리밍은 `stream_options.include_usage=true` 추가)
- Bucket upsert: PostgreSQL `INSERT ... ON CONFLICT (bucket_at, module, model) DO UPDATE` (atomic)
- Admin: `/api/v1/admin/llm-cost/**` 4 엔드포인트 + 프론트 단일 페이지 (Spec 2 KpiCard/StackedBarChart 답습 + 신규 LineChart)

**Tech Stack:** Java 21 + Spring Boot 4.0.5, Spring Data JPA, PostgreSQL, JUnit 5, Mockito, MockMvc + `@WebMvcTest`. Frontend: React + Vite + Vitest + RTL + Recharts + Tailwind.

---

## Decisions Frozen From Spec

| # | 결정 |
|---|---|
| 1 | 신규 `metrics` 모듈 (admin 비대화 방지 + DDD 분리) |
| 2 | ApplicationEvent 발행 + `@Async @EventListener` (5개 클라이언트가 metrics 에 의존 X) |
| 3 | 1시간 UTC 버킷, `(bucket_at, module, model)` UNIQUE → upsert |
| 4 | 정적 enum 가격표 (`LlmModelPricing`) |
| 5 | USD 적재, frontend 정적 환율 상수로 KRW 표시 |
| 6 | OpenAI `usage.{prompt_tokens, completion_tokens}` 파싱. Q&A 스트리밍은 `stream_options.include_usage=true` |

## File Structure

### Backend — 신규
- `metrics/package-info.java`
- `metrics/domain/model/LlmCostBucket.java` — JPA 엔티티
- `metrics/domain/model/LlmModule.java` — enum (QNA/GUIDE/EMBEDDING/INGESTION/ELIGIBILITY)
- `metrics/domain/model/LlmModelPricing.java` — 가격 enum + `calculate(promptTokens, completionTokens)`
- `metrics/domain/repository/LlmCostBucketRepository.java` — JPA repo + `@Query` upsert
- `metrics/application/event/LlmCallRecorded.java` — record
- `metrics/application/event/LlmCallRecordedListener.java` — `@Async @EventListener`
- `metrics/application/service/LlmCostBucketService.java` — `recordCall(...)` 도메인 서비스
- `metrics/application/service/LlmCostQueryService.java` — admin 조회용 집계
- `admin/presentation/controller/AdminLlmCostApi.java` — Swagger 인터페이스
- `admin/presentation/controller/AdminLlmCostController.java` — REST controller
- `admin/application/service/AdminLlmCostService.java` — 조회/집계 dispatch
- `admin/presentation/dto/response/LlmCostKpiResponse.java`
- `admin/presentation/dto/response/LlmCostSeriesResponse.java`
- `admin/presentation/dto/response/LlmCostModuleDailyResponse.java`
- `admin/presentation/dto/response/LlmCostModelSummaryResponse.java`
- `backend/src/main/resources/sql/2026-05-05-llm-cost-bucket.sql` — DDL

### Backend — 수정
- `qna/infrastructure/external/OpenAiQnaClient.java` — `stream_options.include_usage` 추가 + usage chunk 파싱 + event publish
- `guide/infrastructure/external/OpenAiChatClient.java` — usage 파싱 + event publish (2 메서드)
- `rag/infrastructure/external/OpenAiEmbeddingClient.java` — usage 파싱 + event publish
- `ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java` — usage 파싱 + event publish
- `eligibility/infrastructure/external/OpenAiEligibilityRuleClient.java` — usage 파싱 + event publish (2 메서드)
- `backend/src/main/resources/application.yml` — 신규 설정 키 2개

### Frontend — 신규
- `frontend/src/apis/admin.llmCost.api.ts`
- `frontend/src/pages/admin/AdminLlmCostPage.tsx`
- `frontend/src/components/admin/llmCost/LlmCostKpiSection.tsx`
- `frontend/src/components/admin/llmCost/LlmCostLineChart.tsx`
- `frontend/src/components/admin/llmCost/LlmCostStackedBar.tsx`
- `frontend/src/components/admin/llmCost/LlmCostModelTable.tsx`
- `frontend/src/components/admin/llmCost/LlmCostRangeToggle.tsx`
- `frontend/src/hooks/useAdminLlmCost.ts`
- 컴포넌트별 `__tests__/*.test.tsx`

### Frontend — 수정
- `frontend/src/components/layout/AdminSidebar.tsx` — `/admin/llm-cost` 항목 추가 (활성)
- `frontend/src/router` (라우트 등록 위치) — `/admin/llm-cost` 라우트 등록

---

# Stage A — 도메인 모델 + 마이그레이션

## Task A1: `LlmModule` enum

**Files:**
- Create: `backend/src/main/java/com/youthfit/metrics/package-info.java`
- Create: `backend/src/main/java/com/youthfit/metrics/domain/model/LlmModule.java`

- [ ] **Step 1: package-info.java 작성**

```java
/**
 * Metrics 모듈 — LLM 호출/비용 측정 도메인.
 * <p>
 * 다른 도메인(qna/guide/rag/ingestion/eligibility)이 OpenAI 호출 직후 ApplicationEvent 를 발행하면,
 * 본 모듈의 listener 가 1시간 단위 bucket 으로 upsert 적재한다.
 */
package com.youthfit.metrics;
```

- [ ] **Step 2: enum 작성**

```java
package com.youthfit.metrics.domain.model;

public enum LlmModule {
    QNA,
    GUIDE,
    EMBEDDING,
    INGESTION,
    ELIGIBILITY
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/youthfit/metrics/
git commit -m "feat(metrics): LlmModule enum + 모듈 패키지 신설"
```

---

## Task A2: `LlmModelPricing` enum + 토큰 → USD 계산

**Files:**
- Create: `backend/src/main/java/com/youthfit/metrics/domain/model/LlmModelPricing.java`
- Create: `backend/src/test/java/com/youthfit/metrics/domain/model/LlmModelPricingTest.java`

- [ ] **Step 1: 단위 테스트 작성 (TDD)**

```java
package com.youthfit.metrics.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class LlmModelPricingTest {

    @Test
    void gpt4oMini_은_promptTokens_과_completionTokens_을_각각_과금한다() {
        LlmModelPricing pricing = LlmModelPricing.GPT_4O_MINI;
        // 1000 prompt + 500 completion
        // input = 1000 * 0.000150 / 1000 = 0.000150
        // output = 500 * 0.000600 / 1000 = 0.000300
        // total = 0.000450
        BigDecimal cost = pricing.calculate(1000, 500);
        assertThat(cost).isEqualByComparingTo("0.000450");
    }

    @Test
    void embedding_모델은_completion_을_무시한다() {
        LlmModelPricing pricing = LlmModelPricing.TEXT_EMBEDDING_3_SMALL;
        // 10000 prompt * 0.00002 / 1000 = 0.000200
        BigDecimal cost = pricing.calculate(10000, 999); // completion 999는 무시
        assertThat(cost).isEqualByComparingTo("0.000200");
    }

    @Test
    void of_는_modelId_로_매칭하고_미등록은_UNKNOWN_을_반환한다() {
        assertThat(LlmModelPricing.of("gpt-4o-mini")).isEqualTo(LlmModelPricing.GPT_4O_MINI);
        assertThat(LlmModelPricing.of("nonexistent-model-x")).isEqualTo(LlmModelPricing.UNKNOWN);
    }

    @Test
    void UNKNOWN_은_언제나_0_을_반환한다() {
        assertThat(LlmModelPricing.UNKNOWN.calculate(99999, 99999)).isEqualByComparingTo("0");
    }

    @Test
    void zero_tokens_도_안전하게_0_을_반환한다() {
        assertThat(LlmModelPricing.GPT_4O_MINI.calculate(0, 0)).isEqualByComparingTo("0");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.metrics.domain.model.LlmModelPricingTest"`
Expected: FAIL (LlmModelPricing 미정의)

- [ ] **Step 3: enum 구현**

```java
package com.youthfit.metrics.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum LlmModelPricing {
    GPT_4O_MINI("gpt-4o-mini", new BigDecimal("0.000150"), new BigDecimal("0.000600")),
    GPT_4O("gpt-4o", new BigDecimal("0.0025"), new BigDecimal("0.010")),
    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small", new BigDecimal("0.00002"), BigDecimal.ZERO),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large", new BigDecimal("0.00013"), BigDecimal.ZERO),
    UNKNOWN("__unknown__", BigDecimal.ZERO, BigDecimal.ZERO);

    private static final BigDecimal PER_1K = BigDecimal.valueOf(1000);

    private final String modelId;
    private final BigDecimal inputPer1K;
    private final BigDecimal outputPer1K;

    LlmModelPricing(String modelId, BigDecimal inputPer1K, BigDecimal outputPer1K) {
        this.modelId = modelId;
        this.inputPer1K = inputPer1K;
        this.outputPer1K = outputPer1K;
    }

    public static LlmModelPricing of(String model) {
        if (model == null) return UNKNOWN;
        for (LlmModelPricing p : values()) {
            if (p.modelId.equals(model)) return p;
        }
        return UNKNOWN;
    }

    public BigDecimal calculate(long promptTokens, long completionTokens) {
        BigDecimal input = inputPer1K.multiply(BigDecimal.valueOf(promptTokens)).divide(PER_1K, 6, RoundingMode.HALF_UP);
        BigDecimal output = outputPer1K.multiply(BigDecimal.valueOf(completionTokens)).divide(PER_1K, 6, RoundingMode.HALF_UP);
        return input.add(output);
    }

    public String modelId() {
        return modelId;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.metrics.domain.model.LlmModelPricingTest"`
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/metrics/domain/model/LlmModelPricing.java \
        backend/src/test/java/com/youthfit/metrics/domain/model/LlmModelPricingTest.java
git commit -m "feat(metrics): LlmModelPricing 가격 enum + 토큰 → USD 계산"
```

---

## Task A3: `LlmCostBucket` JPA 엔티티

**Files:**
- Create: `backend/src/main/java/com/youthfit/metrics/domain/model/LlmCostBucket.java`

- [ ] **Step 1: 엔티티 작성**

```java
package com.youthfit.metrics.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Table(
    name = "llm_cost_bucket",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_llm_cost_bucket_at_module_model",
        columnNames = {"bucket_at", "module", "model"}
    ),
    indexes = {
        @Index(name = "idx_llm_cost_bucket_at", columnList = "bucket_at DESC"),
        @Index(name = "idx_llm_cost_bucket_module_at", columnList = "module, bucket_at DESC")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmCostBucket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bucket_at", nullable = false)
    private Instant bucketAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 20)
    private LlmModule module;

    @Column(name = "model", nullable = false, length = 60)
    private String model;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    @Column(name = "prompt_tokens", nullable = false)
    private long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private long completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    @Column(name = "estimated_cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static LlmCostBucket initial(Instant bucketAt, LlmModule module, String model,
                                        long promptTokens, long completionTokens, BigDecimal cost,
                                        Instant now) {
        LlmCostBucket b = new LlmCostBucket();
        b.bucketAt = bucketAt;
        b.module = module;
        b.model = model;
        b.callCount = 1;
        b.promptTokens = promptTokens;
        b.completionTokens = completionTokens;
        b.totalTokens = promptTokens + completionTokens;
        b.estimatedCostUsd = cost;
        b.createdAt = now;
        b.updatedAt = now;
        return b;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/metrics/domain/model/LlmCostBucket.java
git commit -m "feat(metrics): LlmCostBucket JPA 엔티티"
```

---

## Task A4: `LlmCostBucketRepository` (upsert + 조회 쿼리)

**Files:**
- Create: `backend/src/main/java/com/youthfit/metrics/domain/repository/LlmCostBucketRepository.java`

- [ ] **Step 1: 리포지토리 작성**

```java
package com.youthfit.metrics.domain.repository;

import com.youthfit.metrics.domain.model.LlmCostBucket;
import com.youthfit.metrics.domain.model.LlmModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface LlmCostBucketRepository extends JpaRepository<LlmCostBucket, Long> {

    /**
     * Atomic upsert. PostgreSQL native query.
     * 신규 행이면 INSERT, 충돌 시 누적 UPDATE.
     */
    @Modifying
    @Query(value = """
            INSERT INTO llm_cost_bucket
                (bucket_at, module, model, call_count, prompt_tokens, completion_tokens,
                 total_tokens, estimated_cost_usd, created_at, updated_at)
            VALUES
                (:bucketAt, :module, :model, 1, :promptTokens, :completionTokens,
                 :totalTokens, :cost, :now, :now)
            ON CONFLICT (bucket_at, module, model) DO UPDATE SET
                call_count = llm_cost_bucket.call_count + 1,
                prompt_tokens = llm_cost_bucket.prompt_tokens + :promptTokens,
                completion_tokens = llm_cost_bucket.completion_tokens + :completionTokens,
                total_tokens = llm_cost_bucket.total_tokens + :totalTokens,
                estimated_cost_usd = llm_cost_bucket.estimated_cost_usd + :cost,
                updated_at = :now
            """, nativeQuery = true)
    void upsert(@Param("bucketAt") Instant bucketAt,
                @Param("module") String module,
                @Param("model") String model,
                @Param("promptTokens") long promptTokens,
                @Param("completionTokens") long completionTokens,
                @Param("totalTokens") long totalTokens,
                @Param("cost") BigDecimal cost,
                @Param("now") Instant now);

    /**
     * KPI 산출 — 기간별 모듈 무관 합계.
     */
    @Query(value = """
            SELECT COALESCE(SUM(estimated_cost_usd), 0) AS cost,
                   COALESCE(SUM(call_count), 0) AS calls
            FROM llm_cost_bucket
            WHERE bucket_at >= :from AND bucket_at < :to
            """, nativeQuery = true)
    Map<String, Object> sumBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 시간별 시계열 — range 윈도우 내 시간 단위 합계.
     */
    @Query(value = """
            SELECT date_trunc('hour', bucket_at) AS bucket_hour,
                   module,
                   SUM(estimated_cost_usd) AS cost
            FROM llm_cost_bucket
            WHERE bucket_at >= :from AND bucket_at < :to
            GROUP BY bucket_hour, module
            ORDER BY bucket_hour
            """, nativeQuery = true)
    List<Object[]> hourlySeries(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 일자별·모듈별 합계 (stacked bar 용).
     */
    @Query(value = """
            SELECT (bucket_at AT TIME ZONE 'Asia/Seoul')::date AS day,
                   module,
                   SUM(estimated_cost_usd) AS cost,
                   SUM(call_count) AS calls
            FROM llm_cost_bucket
            WHERE bucket_at >= :from AND bucket_at < :to
            GROUP BY day, module
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> dailyByModule(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 모델별 합계 (테이블용).
     */
    @Query(value = """
            SELECT model,
                   SUM(call_count) AS calls,
                   SUM(prompt_tokens) AS prompt,
                   SUM(completion_tokens) AS completion,
                   SUM(total_tokens) AS total,
                   SUM(estimated_cost_usd) AS cost
            FROM llm_cost_bucket
            WHERE bucket_at >= :from AND bucket_at < :to
            GROUP BY model
            ORDER BY cost DESC
            """, nativeQuery = true)
    List<Object[]> modelSummary(@Param("from") Instant from, @Param("to") Instant to);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/metrics/domain/repository/LlmCostBucketRepository.java
git commit -m "feat(metrics): LlmCostBucketRepository upsert + 4종 집계 쿼리"
```

---

## Task A5: SQL 마이그레이션

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-05-llm-cost-bucket.sql`

- [ ] **Step 1: DDL 작성**

```sql
-- llm_cost_bucket: 1시간 단위 LLM 호출/비용 집계 (Spec 4)
CREATE TABLE llm_cost_bucket (
    id                  BIGSERIAL PRIMARY KEY,
    bucket_at           TIMESTAMP NOT NULL,
    module              VARCHAR(20) NOT NULL,
    model               VARCHAR(60) NOT NULL,
    call_count          INT NOT NULL DEFAULT 0,
    prompt_tokens       BIGINT NOT NULL DEFAULT 0,
    completion_tokens   BIGINT NOT NULL DEFAULT 0,
    total_tokens        BIGINT NOT NULL DEFAULT 0,
    estimated_cost_usd  NUMERIC(12, 6) NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT uk_llm_cost_bucket_at_module_model UNIQUE (bucket_at, module, model)
);

CREATE INDEX idx_llm_cost_bucket_at ON llm_cost_bucket (bucket_at DESC);
CREATE INDEX idx_llm_cost_bucket_module_at ON llm_cost_bucket (module, bucket_at DESC);

COMMENT ON TABLE llm_cost_bucket IS 'LLM 호출 1시간 버킷 집계 (Spec 4 — admin LLM 비용 대시보드)';
COMMENT ON COLUMN llm_cost_bucket.bucket_at IS 'UTC, hour 단위 truncate';
COMMENT ON COLUMN llm_cost_bucket.module IS 'QNA | GUIDE | EMBEDDING | INGESTION | ELIGIBILITY';
```

- [ ] **Step 2: 마이그레이션 적용 (로컬 DB)**

Run:
```bash
docker compose exec -T postgres psql -U postgres -d youthfit < backend/src/main/resources/sql/2026-05-05-llm-cost-bucket.sql
```

(또는 프로젝트의 DB 적용 절차에 따라 — Flyway 가 적용되면 부팅 시 자동)

Expected: `CREATE TABLE`, `CREATE INDEX × 2`, `COMMENT × 3` 출력

- [ ] **Step 3: 테이블 확인**

Run:
```bash
docker compose exec -T postgres psql -U postgres -d youthfit -c "\d llm_cost_bucket"
```

Expected: 테이블 컬럼 + UNIQUE + 인덱스 정상 출력

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/sql/2026-05-05-llm-cost-bucket.sql
git commit -m "feat(metrics): llm_cost_bucket 테이블 DDL"
```

---

# Stage B — 이벤트 + 비동기 적재 리스너

## Task B1: `LlmCallRecorded` 이벤트 record

**Files:**
- Create: `backend/src/main/java/com/youthfit/metrics/application/event/LlmCallRecorded.java`

- [ ] **Step 1: record 작성**

```java
package com.youthfit.metrics.application.event;

import com.youthfit.metrics.domain.model.LlmModule;

import java.time.Instant;

public record LlmCallRecorded(
        LlmModule module,
        String model,
        int promptTokens,
        int completionTokens,
        Instant calledAt
) {}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit (Task B2 와 묶어서 commit)** — 단독 commit 안 함

---

## Task B2: `LlmCostBucketService` — bucket upsert

**Files:**
- Create: `backend/src/main/java/com/youthfit/metrics/application/service/LlmCostBucketService.java`
- Create: `backend/src/test/java/com/youthfit/metrics/application/service/LlmCostBucketServiceTest.java`

- [ ] **Step 1: 단위 테스트 작성**

```java
package com.youthfit.metrics.application.service;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LlmCostBucketServiceTest {

    @Test
    void recordCall_은_calledAt_을_시간단위로_truncate_해서_upsert_한다() {
        LlmCostBucketRepository repo = mock(LlmCostBucketRepository.class);
        LlmCostBucketService service = new LlmCostBucketService(repo);

        Instant calledAt = Instant.parse("2026-05-06T10:42:17Z");
        LlmCallRecorded event = new LlmCallRecorded(LlmModule.QNA, "gpt-4o-mini", 1000, 500, calledAt);

        service.recordCall(event);

        ArgumentCaptor<Instant> bucketAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repo).upsert(
                bucketAtCaptor.capture(),
                eq("QNA"),
                eq("gpt-4o-mini"),
                eq(1000L),
                eq(500L),
                eq(1500L),
                any(BigDecimal.class),
                any(Instant.class)
        );
        assertThat(bucketAtCaptor.getValue()).isEqualTo(Instant.parse("2026-05-06T10:00:00Z"));
    }

    @Test
    void recordCall_은_미등록_모델도_cost_0_으로_upsert_를_시도한다() {
        LlmCostBucketRepository repo = mock(LlmCostBucketRepository.class);
        LlmCostBucketService service = new LlmCostBucketService(repo);

        LlmCallRecorded event = new LlmCallRecorded(
                LlmModule.GUIDE, "unknown-model-x", 100, 50, Instant.now()
        );

        service.recordCall(event);

        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(repo).upsert(any(), any(), any(), anyLong(), anyLong(), anyLong(),
                            costCaptor.capture(), any());
        assertThat(costCaptor.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.metrics.application.service.LlmCostBucketServiceTest"`
Expected: FAIL (LlmCostBucketService 미정의)

- [ ] **Step 3: 서비스 구현**

```java
package com.youthfit.metrics.application.service;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModelPricing;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class LlmCostBucketService {

    private final LlmCostBucketRepository repository;

    @Transactional
    public void recordCall(LlmCallRecorded event) {
        Instant bucketAt = event.calledAt().truncatedTo(ChronoUnit.HOURS);
        long prompt = event.promptTokens();
        long completion = event.completionTokens();
        BigDecimal cost = LlmModelPricing.of(event.model())
                .calculate(prompt, completion);
        repository.upsert(
                bucketAt,
                event.module().name(),
                event.model(),
                prompt,
                completion,
                prompt + completion,
                cost,
                Instant.now()
        );
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.metrics.application.service.LlmCostBucketServiceTest"`
Expected: 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/metrics/application/event/LlmCallRecorded.java \
        backend/src/main/java/com/youthfit/metrics/application/service/LlmCostBucketService.java \
        backend/src/test/java/com/youthfit/metrics/application/service/LlmCostBucketServiceTest.java
git commit -m "feat(metrics): LlmCallRecorded 이벤트 + LlmCostBucketService.recordCall (시간 truncate + cost 산출)"
```

---

## Task B3: `LlmCallRecordedListener` — `@Async @EventListener`

**Files:**
- Create: `backend/src/main/java/com/youthfit/metrics/application/event/LlmCallRecordedListener.java`

- [ ] **Step 1: listener 작성 (Spec 3 패턴 답습)**

```java
package com.youthfit.metrics.application.event;

import com.youthfit.metrics.application.service.LlmCostBucketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmCallRecordedListener {

    private final LlmCostBucketService service;

    @Async
    @EventListener
    public void onLlmCall(LlmCallRecorded event) {
        try {
            service.recordCall(event);
        } catch (Exception e) {
            log.warn("LLM 비용 적재 실패 (정상 흐름 진행): module={}, model={}, calledAt={}",
                    event.module(), event.model(), event.calledAt(), e);
        }
    }
}
```

- [ ] **Step 2: `@EnableAsync` 활성 확인**

Run:
```bash
grep -r "@EnableAsync" backend/src/main/java/ | head
```

Expected: 적어도 1개 위치(예: `YouthfitApplication.java` 또는 별도 config)에 활성. 없으면 `backend/src/main/java/com/youthfit/YouthfitApplication.java` 클래스에 `@EnableAsync` 추가.

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/youthfit/metrics/application/event/LlmCallRecordedListener.java
git commit -m "feat(metrics): LlmCallRecordedListener 비동기 적재 (Spec 3 패턴 답습)"
```

---

## Task B4: 통합 테스트 — 이벤트 → repository 적재

**Files:**
- Create: `backend/src/test/java/com/youthfit/metrics/application/event/LlmCallRecordedListenerIntegrationTest.java`

- [ ] **Step 1: 통합 테스트 작성**

```java
package com.youthfit.metrics.application.event;

import com.youthfit.metrics.domain.model.LlmCostBucket;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class LlmCallRecordedListenerIntegrationTest {

    @Autowired ApplicationEventPublisher publisher;
    @Autowired LlmCostBucketRepository repository;

    @Test
    void 같은_시간대_같은_모델_5번_호출_시_call_count_5_적재() {
        repository.deleteAll();
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            publisher.publishEvent(new LlmCallRecorded(
                    LlmModule.QNA, "gpt-4o-mini", 100, 50, now
            ));
        }

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<LlmCostBucket> all = repository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getCallCount()).isEqualTo(5);
            assertThat(all.get(0).getPromptTokens()).isEqualTo(500);
            assertThat(all.get(0).getCompletionTokens()).isEqualTo(250);
        });
    }
}
```

- [ ] **Step 2: 통합 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.metrics.application.event.LlmCallRecordedListenerIntegrationTest"`
Expected: PASS (`@SpringBootTest` 부팅, `@Async` 풀에서 5건 적재 후 1행 누적)

> Awaitility 가 backend 의존성에 없으면 `testImplementation 'org.awaitility:awaitility'` 추가 필요. 또는 단순 `Thread.sleep(2000)` 으로 대체.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/youthfit/metrics/application/event/LlmCallRecordedListenerIntegrationTest.java
git commit -m "test(metrics): 이벤트 → 1시간 버킷 누적 적재 통합 테스트"
```

---

# Stage C — 5개 OpenAI 클라이언트 hook

> **공통 패턴**: 각 클라이언트에 `ApplicationEventPublisher` 주입 → 호출 직후 `usage` 파싱 → `publishEvent(new LlmCallRecorded(...))`. usage 누락 시 0 으로 처리 + warn 로그.

## Task C1: `OpenAiChatClient` (guide) — usage 파싱 + 이벤트 발행

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`

- [ ] **Step 1: 의존성 + 헬퍼 메서드 추가**

`OpenAiChatClient` 의 import / 필드 / 메서드를 다음과 같이 추가:

```java
// imports (기존에 추가)
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
```

`OpenAiChatClient` 의 필드에 publisher 추가 (기존 `properties` 옆):

```java
private final OpenAiChatProperties properties;
private final ApplicationEventPublisher eventPublisher;
private final RestClient restClient = RestClient.create();
private final ObjectMapper objectMapper = new ObjectMapper();
```

> Lombok `@RequiredArgsConstructor` 가 publisher 도 자동 주입.

클래스 끝에 헬퍼 메서드 추가:

```java
private void emitMetric(JsonNode response) {
    try {
        JsonNode usage = response == null ? null : response.get("usage");
        int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
        int completion = usage == null || !usage.has("completion_tokens") ? 0 : usage.get("completion_tokens").asInt();
        String model = properties.getModel();
        eventPublisher.publishEvent(new LlmCallRecorded(
                LlmModule.GUIDE, model, prompt, completion, Instant.now()
        ));
    } catch (Exception e) {
        log.warn("guide LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
    }
}
```

- [ ] **Step 2: `generateGuide` 응답 검증 직후 `emitMetric` 호출**

기존 `generateGuide` 메서드의 `response == null || !response.has("choices") ...` 체크 직후에 추가:

```java
        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            log.error("OpenAI Chat API 호출 실패: policyId={}", input.policyId());
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 생성에 실패했습니다");
        }

        emitMetric(response);   // ← 추가

        String json = response.get("choices").get(0).get("message").get("content").asText();
```

- [ ] **Step 3: `regenerateWithFeedback` 도 동일 위치에 `emitMetric` 호출 추가**

```java
        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            log.error("OpenAI Chat API 재시도 실패: policyId={}", input.policyId());
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 재생성 실패");
        }

        emitMetric(response);   // ← 추가
```

- [ ] **Step 4: 단위 테스트 추가 / 갱신**

**File:** `backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientMetricTest.java`

```java
package com.youthfit.guide.infrastructure.external;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenAiChatClientMetricTest {

    @Test
    void emitMetric_은_usage_를_파싱해_GUIDE_모듈_이벤트를_발행한다() throws Exception {
        OpenAiChatProperties props = mock(OpenAiChatProperties.class);
        when(props.getModel()).thenReturn("gpt-4o-mini");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        OpenAiChatClient client = new OpenAiChatClient(props, publisher);

        JsonNode response = new ObjectMapper().readTree("""
                {"choices":[{"message":{"content":"{}"}}],
                 "usage":{"prompt_tokens": 1234, "completion_tokens": 567}}
                """);

        Method m = OpenAiChatClient.class.getDeclaredMethod("emitMetric", JsonNode.class);
        m.setAccessible(true);
        m.invoke(client, response);

        ArgumentCaptor<LlmCallRecorded> captor = ArgumentCaptor.forClass(LlmCallRecorded.class);
        verify(publisher).publishEvent(captor.capture());
        LlmCallRecorded event = captor.getValue();
        assertThat(event.module()).isEqualTo(LlmModule.GUIDE);
        assertThat(event.model()).isEqualTo("gpt-4o-mini");
        assertThat(event.promptTokens()).isEqualTo(1234);
        assertThat(event.completionTokens()).isEqualTo(567);
    }

    @Test
    void usage_누락_시_0_으로_적재_시도() throws Exception {
        OpenAiChatProperties props = mock(OpenAiChatProperties.class);
        when(props.getModel()).thenReturn("gpt-4o-mini");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        OpenAiChatClient client = new OpenAiChatClient(props, publisher);

        JsonNode response = new ObjectMapper().readTree("""
                {"choices":[{"message":{"content":"{}"}}]}
                """);

        Method m = OpenAiChatClient.class.getDeclaredMethod("emitMetric", JsonNode.class);
        m.setAccessible(true);
        m.invoke(client, response);

        ArgumentCaptor<LlmCallRecorded> captor = ArgumentCaptor.forClass(LlmCallRecorded.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().promptTokens()).isZero();
        assertThat(captor.getValue().completionTokens()).isZero();
    }
}
```

- [ ] **Step 5: 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.infrastructure.external.OpenAiChatClientMetricTest"`
Expected: 2 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java \
        backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientMetricTest.java
git commit -m "feat(guide): OpenAiChatClient usage 파싱 + LlmCallRecorded 이벤트 발행"
```

---

## Task C2: `OpenAiEmbeddingClient` (rag)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java`

- [ ] **Step 1: 필드 + import 추가**

```java
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
```

```java
private final OpenAiEmbeddingProperties properties;
private final ApplicationEventPublisher eventPublisher;
private final RestClient restClient = RestClient.create();
```

- [ ] **Step 2: `embedBatch` 응답 검증 직후 emit 추가**

`response == null || !response.has("data") ...` 체크 직후에:

```java
        if (response == null || !response.has("data")) {
            log.error("OpenAI 임베딩 API 호출 실패");
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "임베딩 생성에 실패했습니다");
        }

        try {
            JsonNode usage = response.get("usage");
            int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
            eventPublisher.publishEvent(new LlmCallRecorded(
                    LlmModule.EMBEDDING, properties.getModel(), prompt, 0, Instant.now()
            ));
        } catch (Exception e) {
            log.warn("embedding LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
        }
```

- [ ] **Step 3: 단위 테스트 추가**

**File:** `backend/src/test/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClientMetricTest.java`

```java
package com.youthfit.rag.infrastructure.external;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenAiEmbeddingClientMetricTest {

    /**
     * 단위 테스트는 RestClient 모킹이 까다로워 본 테스트는 통합 테스트(F1) 로 검증한다.
     * 여기서는 properties / publisher 의 인스턴스 주입과 컴파일을 검증한다.
     */
    @Test
    void publisher_는_정상_주입된다() {
        OpenAiEmbeddingProperties props = mock(OpenAiEmbeddingProperties.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(props, publisher);
        assertThat(client).isNotNull();
    }
}
```

> 임베딩 클라이언트는 streaming 아님 + RestClient mocking 비용 큼. 실제 emit 검증은 Stage F E2E 에서.

- [ ] **Step 4: 컴파일 + 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.infrastructure.external.OpenAiEmbeddingClientMetricTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java \
        backend/src/test/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClientMetricTest.java
git commit -m "feat(rag): OpenAiEmbeddingClient usage 파싱 + LlmCallRecorded 이벤트 (EMBEDDING 모듈)"
```

---

## Task C3: `OpenAiPolicyPeriodExtractor` (ingestion)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java`

- [ ] **Step 1: 필드 + import 추가**

```java
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
```

```java
private final OpenAiPolicyPeriodProperties properties;
private final ObjectMapper objectMapper;
private final ApplicationEventPublisher eventPublisher;
private final RestClient restClient = RestClient.create();
```

- [ ] **Step 2: `extractPeriod` 응답 직후 emit 추가**

`response == null || !response.has("choices") ...` 체크 *직후* (`String content = response.get("choices")...` 위에) :

```java
            if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
                log.warn("기간 추출 응답이 비어 있습니다: title={}", title);
                return PolicyPeriod.empty();
            }

            try {
                JsonNode usage = response.get("usage");
                int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
                int completion = usage == null || !usage.has("completion_tokens") ? 0 : usage.get("completion_tokens").asInt();
                eventPublisher.publishEvent(new LlmCallRecorded(
                        LlmModule.INGESTION, properties.getModel(), prompt, completion, Instant.now()
                ));
            } catch (Exception e) {
                log.warn("ingestion LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
            }

            String content = response.get("choices").get(0).get("message").get("content").asText();
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java
git commit -m "feat(ingestion): OpenAiPolicyPeriodExtractor usage 파싱 + LlmCallRecorded 이벤트"
```

---

## Task C4: `OpenAiEligibilityRuleClient` (eligibility)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/infrastructure/external/OpenAiEligibilityRuleClient.java`

- [ ] **Step 1: 필드 + import 추가**

```java
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
```

```java
private final OpenAiEligibilityRuleProperties properties;
private final ApplicationEventPublisher eventPublisher;
private final RestClient restClient = RestClient.create();
private final ObjectMapper objectMapper = new ObjectMapper();
```

- [ ] **Step 2: `callOpenAi` 헬퍼 안에서 emit (1번만 추가하면 두 메서드 모두 커버)**

기존 `callOpenAi` 의 `JsonNode root = objectMapper.readTree(responseBody);` 이후에:

```java
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("OpenAI 응답에 choices 배열이 없거나 비어있음");
            }

            try {
                JsonNode usage = root.get("usage");
                int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
                int completion = usage == null || !usage.has("completion_tokens") ? 0 : usage.get("completion_tokens").asInt();
                eventPublisher.publishEvent(new LlmCallRecorded(
                        LlmModule.ELIGIBILITY, properties.getModel(), prompt, completion, Instant.now()
                ));
            } catch (Exception e) {
                log.warn("eligibility LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
            }

            String content = choices.get(0).path("message").path("content").asText();
            // ... 기존 로직 ...
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/youthfit/eligibility/infrastructure/external/OpenAiEligibilityRuleClient.java
git commit -m "feat(eligibility): OpenAiEligibilityRuleClient usage 파싱 + LlmCallRecorded 이벤트"
```

---

## Task C5: `OpenAiQnaClient` (qna) — streaming usage

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java`

> Q&A 는 `stream: true` 로 호출. OpenAI 는 `stream_options.include_usage = true` 를 보내면 마지막 SSE 청크에 `usage` 를 포함한다. 기존 `readStreamResponse` 가 `[DONE]` 을 만나면 break 하지만, 그 직전에 usage 청크가 포함된다.

- [ ] **Step 1: 필드 + import 추가**

```java
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
```

```java
private final OpenAiQnaProperties properties;
private final ApplicationEventPublisher eventPublisher;
private final RestClient restClient = RestClient.create();
private final ObjectMapper objectMapper = new ObjectMapper();
```

- [ ] **Step 2: `requestBody` 에 `stream_options` 추가**

기존 `requestBody = Map.of(...)` 를 다음으로 교체:

```java
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "temperature", 0.2,
                "seed", 1,
                "stream", true,
                "stream_options", Map.of("include_usage", true),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );
```

- [ ] **Step 3: `readStreamResponse` 에서 usage 청크 파싱**

기존 메서드를 다음과 같이 변경 (usage 청크는 `choices` 가 비어있고 `usage` 필드만 가짐):

```java
private String readStreamResponse(InputStream inputStream, Consumer<String> chunkConsumer) throws Exception {
    StringBuilder fullAnswer = new StringBuilder();
    int promptTokens = 0;
    int completionTokens = 0;

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data: ")) {
                continue;
            }
            String data = line.substring(6).trim();
            if ("[DONE]".equals(data)) {
                break;
            }

            JsonNode node = objectMapper.readTree(data);

            // usage 청크 (choices 비어있음)
            JsonNode usage = node.get("usage");
            if (usage != null && !usage.isNull()) {
                if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").asInt();
                if (usage.has("completion_tokens")) completionTokens = usage.get("completion_tokens").asInt();
                continue;
            }

            JsonNode choices = node.get("choices");
            if (choices == null || choices.isEmpty()) {
                continue;
            }
            JsonNode delta = choices.get(0).get("delta");
            if (delta == null || !delta.has("content")) {
                continue;
            }
            String content = delta.get("content").asText();
            if (content != null && !content.isEmpty()) {
                fullAnswer.append(content);
                chunkConsumer.accept(content);
            }
        }
    }

    // 스트림 종료 후 메트릭 발행 (usage 누락 시 0)
    try {
        eventPublisher.publishEvent(new LlmCallRecorded(
                LlmModule.QNA, properties.getModel(), promptTokens, completionTokens, Instant.now()
        ));
    } catch (Exception e) {
        log.warn("qna LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
    }

    return fullAnswer.toString();
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 단위 테스트 — usage 청크 파싱**

**File:** `backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClientMetricTest.java`

```java
package com.youthfit.qna.infrastructure.external;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenAiQnaClientMetricTest {

    @Test
    void readStreamResponse_는_usage_청크를_파싱해_QNA_이벤트를_발행한다() throws Exception {
        OpenAiQnaProperties props = mock(OpenAiQnaProperties.class);
        when(props.getModel()).thenReturn("gpt-4o-mini");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        OpenAiQnaClient client = new OpenAiQnaClient(props, publisher);

        // SSE 형식 더미 — content chunk 2 + usage chunk 1 + [DONE]
        String sse = """
                data: {"choices":[{"delta":{"content":"안녕"}}]}

                data: {"choices":[{"delta":{"content":"하세요"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":34,"total_tokens":46}}

                data: [DONE]

                """;
        var inputStream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        StringBuilder collected = new StringBuilder();
        Consumer<String> consumer = collected::append;

        Method m = OpenAiQnaClient.class.getDeclaredMethod("readStreamResponse",
                java.io.InputStream.class, Consumer.class);
        m.setAccessible(true);
        Object result = m.invoke(client, inputStream, consumer);

        assertThat(result).isEqualTo("안녕하세요");
        assertThat(collected.toString()).isEqualTo("안녕하세요");

        ArgumentCaptor<LlmCallRecorded> captor = ArgumentCaptor.forClass(LlmCallRecorded.class);
        verify(publisher).publishEvent(captor.capture());
        LlmCallRecorded event = captor.getValue();
        assertThat(event.module()).isEqualTo(LlmModule.QNA);
        assertThat(event.promptTokens()).isEqualTo(12);
        assertThat(event.completionTokens()).isEqualTo(34);
    }
}
```

- [ ] **Step 6: 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.infrastructure.external.OpenAiQnaClientMetricTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java \
        backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClientMetricTest.java
git commit -m "feat(qna): stream_options.include_usage 추가 + usage 청크 파싱 + LlmCallRecorded 이벤트"
```

---

# Stage D — Admin 조회 API

## Task D1: 응답 DTO 4종

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/LlmCostKpiResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/LlmCostSeriesResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/LlmCostModuleDailyResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/LlmCostModelSummaryResponse.java`

- [ ] **Step 1: KPI DTO**

```java
package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;

public record LlmCostKpiResponse(
        BigDecimal todayCostUsd,
        BigDecimal thisWeekCostUsd,
        BigDecimal thisMonthCostUsd,
        long thisMonthCallCount,
        BigDecimal usdToKrwRate,
        BigDecimal lastMonthCostUsd
) {}
```

- [ ] **Step 2: Series DTO (시간별 시계열, 모듈별 분리)**

```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.metrics.domain.model.LlmModule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record LlmCostSeriesResponse(
        String range,
        List<Point> points
) {
    public record Point(
            Instant at,
            Map<LlmModule, BigDecimal> costByModule
    ) {}
}
```

- [ ] **Step 3: 일자별 모듈 분포 DTO**

```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.metrics.domain.model.LlmModule;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LlmCostModuleDailyResponse(
        LocalDate date,
        LlmModule module,
        BigDecimal totalCostUsd,
        long callCount
) {}
```

- [ ] **Step 4: 모델별 합계 DTO**

```java
package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;

public record LlmCostModelSummaryResponse(
        String model,
        long callCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal totalCostUsd,
        BigDecimal costShare
) {}
```

- [ ] **Step 5: 컴파일 + commit**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/admin/presentation/dto/response/LlmCost*.java
git commit -m "feat(admin): LLM 비용 응답 DTO 4종 (Kpi/Series/ModuleDaily/ModelSummary)"
```

---

## Task D2: `AdminLlmCostService` — 조회/집계

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/application/service/AdminLlmCostService.java`

- [ ] **Step 1: 서비스 구현**

```java
package com.youthfit.admin.application.service;

import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminLlmCostService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LlmCostBucketRepository repository;

    @Value("${youthfit.metrics.llm-cost.usd-to-krw:1350}")
    private BigDecimal usdToKrwRate;

    @Transactional(readOnly = true)
    public LlmCostKpiResponse getKpi() {
        ZonedDateTime nowKst = ZonedDateTime.now(KST);
        LocalDate todayKst = nowKst.toLocalDate();
        Instant todayStart = todayKst.atStartOfDay(KST).toInstant();
        Instant tomorrowStart = todayKst.plusDays(1).atStartOfDay(KST).toInstant();

        DayOfWeek dow = todayKst.getDayOfWeek();
        LocalDate weekStart = todayKst.minusDays(dow.getValue() - 1L); // Mon=1
        Instant weekStartInst = weekStart.atStartOfDay(KST).toInstant();

        LocalDate monthStart = todayKst.withDayOfMonth(1);
        Instant monthStartInst = monthStart.atStartOfDay(KST).toInstant();

        LocalDate lastMonthStart = monthStart.minusMonths(1);
        Instant lastMonthStartInst = lastMonthStart.atStartOfDay(KST).toInstant();

        BigDecimal today = costSum(todayStart, tomorrowStart);
        BigDecimal week = costSum(weekStartInst, tomorrowStart);
        BigDecimal month = costSum(monthStartInst, tomorrowStart);
        BigDecimal lastMonth = costSum(lastMonthStartInst, monthStartInst);
        long monthCalls = callsSum(monthStartInst, tomorrowStart);

        return new LlmCostKpiResponse(today, week, month, monthCalls, usdToKrwRate, lastMonth);
    }

    @Transactional(readOnly = true)
    public LlmCostSeriesResponse getSeries(String range) {
        Range r = parseRange(range);
        List<Object[]> rows = repository.hourlySeries(r.from(), r.to());

        // bucket_hour → moduleMap
        Map<Instant, Map<LlmModule, BigDecimal>> grouped = new TreeMap<>();
        for (Object[] row : rows) {
            Instant at = ((Timestamp) row[0]).toInstant();
            LlmModule m = LlmModule.valueOf((String) row[1]);
            BigDecimal cost = (BigDecimal) row[2];
            grouped.computeIfAbsent(at, k -> new EnumMap<>(LlmModule.class)).put(m, cost);
        }

        List<LlmCostSeriesResponse.Point> points = new ArrayList<>();
        for (Map.Entry<Instant, Map<LlmModule, BigDecimal>> e : grouped.entrySet()) {
            points.add(new LlmCostSeriesResponse.Point(e.getKey(), e.getValue()));
        }
        return new LlmCostSeriesResponse(range, points);
    }

    @Transactional(readOnly = true)
    public List<LlmCostModuleDailyResponse> getDailyByModule(String range) {
        Range r = parseRange(range);
        List<Object[]> rows = repository.dailyByModule(r.from(), r.to());
        List<LlmCostModuleDailyResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = ((Date) row[0]).toLocalDate();
            LlmModule module = LlmModule.valueOf((String) row[1]);
            BigDecimal cost = (BigDecimal) row[2];
            long calls = ((Number) row[3]).longValue();
            result.add(new LlmCostModuleDailyResponse(date, module, cost, calls));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LlmCostModelSummaryResponse> getModelSummary(String range) {
        Range r = parseRange(range);
        List<Object[]> rows = repository.modelSummary(r.from(), r.to());

        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : rows) {
            total = total.add((BigDecimal) row[5]);
        }
        if (total.compareTo(BigDecimal.ZERO) == 0) total = BigDecimal.ONE;

        List<LlmCostModelSummaryResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String model = (String) row[0];
            long calls = ((Number) row[1]).longValue();
            long prompt = ((Number) row[2]).longValue();
            long completion = ((Number) row[3]).longValue();
            long totalT = ((Number) row[4]).longValue();
            BigDecimal cost = (BigDecimal) row[5];
            BigDecimal share = cost.multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP);
            result.add(new LlmCostModelSummaryResponse(model, calls, prompt, completion, totalT, cost, share));
        }
        return result;
    }

    private BigDecimal costSum(Instant from, Instant to) {
        Map<String, Object> row = repository.sumBetween(from, to);
        Object cost = row.get("cost");
        if (cost == null) return BigDecimal.ZERO;
        return (BigDecimal) cost;
    }

    private long callsSum(Instant from, Instant to) {
        Map<String, Object> row = repository.sumBetween(from, to);
        Object calls = row.get("calls");
        if (calls == null) return 0;
        return ((Number) calls).longValue();
    }

    private Range parseRange(String range) {
        Instant now = Instant.now();
        Instant from = switch (range == null ? "7d" : range.toLowerCase(Locale.ROOT)) {
            case "24h" -> now.minus(Duration.ofHours(24));
            case "7d" -> now.minus(Duration.ofDays(7));
            case "30d" -> now.minus(Duration.ofDays(30));
            default -> now.minus(Duration.ofDays(7));
        };
        return new Range(from, now);
    }

    private record Range(Instant from, Instant to) {}
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/application/service/AdminLlmCostService.java
git commit -m "feat(admin): AdminLlmCostService — KPI/시리즈/모듈일자/모델합계 4종 조회"
```

---

## Task D3: `AdminLlmCostApi` 인터페이스 + Controller

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminLlmCostApi.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminLlmCostController.java`

- [ ] **Step 1: Swagger 인터페이스**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Admin LLM Cost", description = "어드민 — LLM 호출/비용 대시보드 (Spec 4)")
public interface AdminLlmCostApi {

    @Operation(summary = "오늘/이번주/이번달 비용 KPI")
    ResponseEntity<LlmCostKpiResponse> getKpi();

    @Operation(summary = "시간별 비용 시계열 (모듈별 line 차트용)")
    ResponseEntity<LlmCostSeriesResponse> getSeries(
            @Parameter(description = "24h | 7d | 30d (기본 7d)")
            @RequestParam(required = false, defaultValue = "7d") String range);

    @Operation(summary = "일자별·모듈별 비용 (stacked bar 용)")
    ResponseEntity<List<LlmCostModuleDailyResponse>> getDailyByModule(
            @Parameter(description = "7d | 30d (기본 7d)")
            @RequestParam(required = false, defaultValue = "7d") String range);

    @Operation(summary = "모델별 호출/토큰/비용 합계 (테이블용)")
    ResponseEntity<List<LlmCostModelSummaryResponse>> getModelSummary(
            @Parameter(description = "7d | 30d (기본 7d)")
            @RequestParam(required = false, defaultValue = "7d") String range);
}
```

- [ ] **Step 2: Controller**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminLlmCostService;
import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.auth.presentation.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/llm-cost")
@RequiredArgsConstructor
@RequireAdmin
public class AdminLlmCostController implements AdminLlmCostApi {

    private final AdminLlmCostService service;

    @Override
    @GetMapping("/kpi")
    public ResponseEntity<LlmCostKpiResponse> getKpi() {
        return ResponseEntity.ok(service.getKpi());
    }

    @Override
    @GetMapping("/series")
    public ResponseEntity<LlmCostSeriesResponse> getSeries(@RequestParam(required = false, defaultValue = "7d") String range) {
        return ResponseEntity.ok(service.getSeries(range));
    }

    @Override
    @GetMapping("/by-module")
    public ResponseEntity<List<LlmCostModuleDailyResponse>> getDailyByModule(@RequestParam(required = false, defaultValue = "7d") String range) {
        return ResponseEntity.ok(service.getDailyByModule(range));
    }

    @Override
    @GetMapping("/by-model")
    public ResponseEntity<List<LlmCostModelSummaryResponse>> getModelSummary(@RequestParam(required = false, defaultValue = "7d") String range) {
        return ResponseEntity.ok(service.getModelSummary(range));
    }
}
```

> **`@RequireAdmin` 위치 확인**: Spec 1 에서 정한 어노테이션 패키지 (`auth.presentation.annotation`) — 실제 위치 확인 후 import 조정. 기존 `AdminQnaCacheController` 의 import 참조.

- [ ] **Step 3: 컴파일 + commit**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/admin/presentation/controller/AdminLlmCost*.java
git commit -m "feat(admin): AdminLlmCostController — 4 엔드포인트 + Swagger Api 인터페이스"
```

---

## Task D4: 슬라이스 테스트 (`@WebMvcTest`)

**Files:**
- Create: `backend/src/test/java/com/youthfit/admin/presentation/controller/AdminLlmCostControllerTest.java`

- [ ] **Step 1: 슬라이스 테스트 작성**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminLlmCostService;
import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.metrics.domain.model.LlmModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminLlmCostController.class)
class AdminLlmCostControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AdminLlmCostService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void kpi_GET_은_200_을_반환한다() throws Exception {
        when(service.getKpi()).thenReturn(new LlmCostKpiResponse(
                new BigDecimal("0.123456"), new BigDecimal("1.234"),
                new BigDecimal("12.34"), 1234L,
                new BigDecimal("1350"), new BigDecimal("11.11")
        ));

        mvc.perform(get("/api/v1/admin/llm-cost/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayCostUsd").exists())
                .andExpect(jsonPath("$.usdToKrwRate").value(1350));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void series_GET_은_range_파라미터를_전달한다() throws Exception {
        when(service.getSeries("24h")).thenReturn(
                new LlmCostSeriesResponse("24h", List.of()));

        mvc.perform(get("/api/v1/admin/llm-cost/series").param("range", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("24h"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void by_module_GET_은_기본값_7d_를_사용한다() throws Exception {
        when(service.getDailyByModule(any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/admin/llm-cost/by-module"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void by_model_GET_은_정렬된_리스트를_반환한다() throws Exception {
        when(service.getModelSummary(any())).thenReturn(List.of(
                new LlmCostModelSummaryResponse("gpt-4o-mini", 100, 1000, 500, 1500,
                        new BigDecimal("1.234"), new BigDecimal("80.00"))
        ));
        mvc.perform(get("/api/v1/admin/llm-cost/by-model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].model").value("gpt-4o-mini"));
    }

    @Test
    void 인증_없이_접근하면_401_또는_403() throws Exception {
        mvc.perform(get("/api/v1/admin/llm-cost/kpi"))
                .andExpect(status().is4xxClientError());
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.admin.presentation.controller.AdminLlmCostControllerTest"`
Expected: 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/youthfit/admin/presentation/controller/AdminLlmCostControllerTest.java
git commit -m "test(admin): AdminLlmCostController 슬라이스 테스트 (4 엔드포인트 + 인증)"
```

---

## Task D5: `application.yml` 설정 키 추가

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 키 추가**

`youthfit:` 섹션에 다음 추가:

```yaml
youthfit:
  metrics:
    llm-cost:
      bucket-retention-days: 90
      usd-to-krw: 1350
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/application.yml
git commit -m "feat(metrics): LLM 비용 환경 변수 (retention 90, usd-to-krw 1350)"
```

---

# Stage E — Frontend

## Task E1: API client

**Files:**
- Create: `frontend/src/apis/admin.llmCost.api.ts`

- [ ] **Step 1: API 함수 작성**

```typescript
import { adminApi } from './admin.api';

export type LlmModule = 'QNA' | 'GUIDE' | 'EMBEDDING' | 'INGESTION' | 'ELIGIBILITY';

export interface LlmCostKpiResponse {
  todayCostUsd: number;
  thisWeekCostUsd: number;
  thisMonthCostUsd: number;
  thisMonthCallCount: number;
  usdToKrwRate: number;
  lastMonthCostUsd: number;
}

export interface LlmCostSeriesPoint {
  at: string;
  costByModule: Partial<Record<LlmModule, number>>;
}

export interface LlmCostSeriesResponse {
  range: string;
  points: LlmCostSeriesPoint[];
}

export interface LlmCostModuleDailyResponse {
  date: string;
  module: LlmModule;
  totalCostUsd: number;
  callCount: number;
}

export interface LlmCostModelSummaryResponse {
  model: string;
  callCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  totalCostUsd: number;
  costShare: number;
}

export const fetchLlmCostKpi = async (): Promise<LlmCostKpiResponse> => {
  const { data } = await adminApi.get('/llm-cost/kpi');
  return data;
};

export const fetchLlmCostSeries = async (range: '24h' | '7d' | '30d'): Promise<LlmCostSeriesResponse> => {
  const { data } = await adminApi.get('/llm-cost/series', { params: { range } });
  return data;
};

export const fetchLlmCostByModule = async (range: '7d' | '30d'): Promise<LlmCostModuleDailyResponse[]> => {
  const { data } = await adminApi.get('/llm-cost/by-module', { params: { range } });
  return data;
};

export const fetchLlmCostByModel = async (range: '7d' | '30d'): Promise<LlmCostModelSummaryResponse[]> => {
  const { data } = await adminApi.get('/llm-cost/by-model', { params: { range } });
  return data;
};
```

> `adminApi` import 경로는 `admin.qnaCache.api.ts` 와 동일하게 맞춤. 실제 위치는 기존 파일 참조.

- [ ] **Step 2: TypeScript 체크 + commit**

```bash
cd frontend && npm run typecheck
git add frontend/src/apis/admin.llmCost.api.ts
git commit -m "feat(frontend): admin.llmCost.api.ts — 4 endpoint client + 타입"
```

---

## Task E2: 차트 컴포넌트 — `LlmCostLineChart` (Recharts `LineChart` 신규)

**Files:**
- Create: `frontend/src/components/admin/llmCost/LlmCostLineChart.tsx`
- Create: `frontend/src/components/admin/llmCost/__tests__/LlmCostLineChart.test.tsx`

- [ ] **Step 1: 테스트 작성**

```typescript
import { render, screen } from '@testing-library/react';
import { LlmCostLineChart } from '../LlmCostLineChart';

describe('LlmCostLineChart', () => {
  it('빈 시리즈는 placeholder 메시지를 표시한다', () => {
    render(<LlmCostLineChart points={[]} />);
    expect(screen.getByText(/데이터 없음/)).toBeInTheDocument();
  });

  it('points 가 있으면 차트가 렌더된다', () => {
    render(
      <LlmCostLineChart
        points={[
          { at: '2026-05-06T10:00:00Z', costByModule: { QNA: 0.12, GUIDE: 0.05 } },
          { at: '2026-05-06T11:00:00Z', costByModule: { QNA: 0.10, EMBEDDING: 0.01 } },
        ]}
      />
    );
    expect(screen.queryByText(/데이터 없음/)).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 컴포넌트 구현**

```typescript
import { LineChart, Line, XAxis, YAxis, Tooltip, Legend, ResponsiveContainer, CartesianGrid } from 'recharts';
import type { LlmCostSeriesPoint, LlmModule } from '@/apis/admin.llmCost.api';

const MODULES: LlmModule[] = ['QNA', 'GUIDE', 'EMBEDDING', 'INGESTION', 'ELIGIBILITY'];
const COLORS: Record<LlmModule, string> = {
  QNA: '#6366f1',
  GUIDE: '#10b981',
  EMBEDDING: '#f59e0b',
  INGESTION: '#ec4899',
  ELIGIBILITY: '#8b5cf6',
};

interface Props {
  points: LlmCostSeriesPoint[];
}

export function LlmCostLineChart({ points }: Props) {
  if (points.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center rounded border border-slate-200 bg-slate-50 text-sm text-slate-500">
        데이터 없음
      </div>
    );
  }

  const data = points.map((p) => ({
    at: new Date(p.at).toLocaleString('ko-KR', { hour: '2-digit', day: '2-digit', month: '2-digit' }),
    ...MODULES.reduce(
      (acc, m) => ({ ...acc, [m]: p.costByModule[m] ?? 0 }),
      {} as Record<LlmModule, number>
    ),
  }));

  return (
    <ResponsiveContainer width="100%" height={320}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="at" tick={{ fontSize: 11 }} />
        <YAxis tickFormatter={(v) => `$${v.toFixed(3)}`} tick={{ fontSize: 11 }} />
        <Tooltip formatter={(v: number) => `$${v.toFixed(4)}`} />
        <Legend />
        {MODULES.map((m) => (
          <Line key={m} type="monotone" dataKey={m} stroke={COLORS[m]} dot={false} strokeWidth={2} />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}
```

- [ ] **Step 3: 테스트 실행**

Run: `cd frontend && npm run test -- LlmCostLineChart`
Expected: 2 tests PASS

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/admin/llmCost/LlmCostLineChart.tsx \
        frontend/src/components/admin/llmCost/__tests__/LlmCostLineChart.test.tsx
git commit -m "feat(frontend): LlmCostLineChart Recharts LineChart 컴포넌트 (5 모듈 색상 구분)"
```

---

## Task E3: KPI / Stacked Bar / 모델 테이블 / 토글 컴포넌트

**Files:**
- Create: `frontend/src/components/admin/llmCost/LlmCostKpiSection.tsx`
- Create: `frontend/src/components/admin/llmCost/LlmCostStackedBar.tsx`
- Create: `frontend/src/components/admin/llmCost/LlmCostModelTable.tsx`
- Create: `frontend/src/components/admin/llmCost/LlmCostRangeToggle.tsx`

- [ ] **Step 1: KPI Section**

```typescript
import { KpiCard } from '@/components/admin/email/KpiCard'; // Spec 2 KpiCard 재사용
import type { LlmCostKpiResponse } from '@/apis/admin.llmCost.api';

interface Props { kpi: LlmCostKpiResponse | undefined; }

export function LlmCostKpiSection({ kpi }: Props) {
  if (!kpi) return null;
  const krw = (usd: number) => `≈ ₩${Math.round(usd * kpi.usdToKrwRate).toLocaleString()}`;
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
      <KpiCard title="오늘 비용" value={`$${kpi.todayCostUsd.toFixed(4)}`} sub={krw(kpi.todayCostUsd)} />
      <KpiCard title="이번주 비용" value={`$${kpi.thisWeekCostUsd.toFixed(4)}`} sub={krw(kpi.thisWeekCostUsd)} />
      <KpiCard title="이번달 비용" value={`$${kpi.thisMonthCostUsd.toFixed(4)}`} sub={krw(kpi.thisMonthCostUsd)} />
      <KpiCard
        title="이번달 호출 수"
        value={kpi.thisMonthCallCount.toLocaleString()}
        sub={kpi.lastMonthCostUsd > 0 ? `전월 $${kpi.lastMonthCostUsd.toFixed(2)}` : ''}
      />
    </div>
  );
}
```

> `KpiCard` 위치는 Spec 2 에서 만든 컴포넌트 — 실제 import 경로 확인.

- [ ] **Step 2: Stacked Bar (Spec 2 `StackedBarChart` 활용)**

```typescript
import type { LlmCostModuleDailyResponse, LlmModule } from '@/apis/admin.llmCost.api';
import { StackedBarChart } from '@/components/charts/StackedBarChart';

const MODULES: LlmModule[] = ['QNA', 'GUIDE', 'EMBEDDING', 'INGESTION', 'ELIGIBILITY'];
const COLORS: Record<LlmModule, string> = {
  QNA: '#6366f1',
  GUIDE: '#10b981',
  EMBEDDING: '#f59e0b',
  INGESTION: '#ec4899',
  ELIGIBILITY: '#8b5cf6',
};

interface Props { rows: LlmCostModuleDailyResponse[]; }

export function LlmCostStackedBar({ rows }: Props) {
  // group by date
  const map = new Map<string, Record<LlmModule, number>>();
  rows.forEach((r) => {
    if (!map.has(r.date)) map.set(r.date, MODULES.reduce((a, m) => ({ ...a, [m]: 0 }), {} as Record<LlmModule, number>));
    map.get(r.date)![r.module] = r.totalCostUsd;
  });
  const data = Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, costs]) => ({ date, ...costs }));

  if (data.length === 0) {
    return <div className="flex h-64 items-center justify-center rounded border border-slate-200 bg-slate-50 text-sm text-slate-500">데이터 없음</div>;
  }
  return <StackedBarChart data={data} xKey="date" series={MODULES.map((m) => ({ key: m, color: COLORS[m], label: m }))} />;
}
```

> `StackedBarChart` 의 API (xKey/series 등) 는 Spec 2 의 실제 시그니처 참조 후 조정.

- [ ] **Step 3: 모델 테이블**

```typescript
import type { LlmCostModelSummaryResponse } from '@/apis/admin.llmCost.api';

interface Props { rows: LlmCostModelSummaryResponse[]; }

export function LlmCostModelTable({ rows }: Props) {
  if (rows.length === 0) {
    return <div className="rounded border border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">호출 기록 없음</div>;
  }
  return (
    <div className="overflow-x-auto rounded border border-slate-200">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2 text-left">모델</th>
            <th className="px-3 py-2 text-right">호출 수</th>
            <th className="px-3 py-2 text-right">입력 토큰</th>
            <th className="px-3 py-2 text-right">출력 토큰</th>
            <th className="px-3 py-2 text-right">총 토큰</th>
            <th className="px-3 py-2 text-right">비용 (USD)</th>
            <th className="px-3 py-2 text-right">비중</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.model} className="border-t border-slate-100">
              <td className="px-3 py-2 font-mono text-xs">{r.model}</td>
              <td className="px-3 py-2 text-right">{r.callCount.toLocaleString()}</td>
              <td className="px-3 py-2 text-right">{r.promptTokens.toLocaleString()}</td>
              <td className="px-3 py-2 text-right">{r.completionTokens.toLocaleString()}</td>
              <td className="px-3 py-2 text-right">{r.totalTokens.toLocaleString()}</td>
              <td className="px-3 py-2 text-right font-semibold">${r.totalCostUsd.toFixed(4)}</td>
              <td className="px-3 py-2 text-right text-slate-500">{r.costShare.toFixed(2)}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 4: Range 토글**

```typescript
type Range = '24h' | '7d' | '30d';
interface Props { value: Range; onChange: (r: Range) => void; }

export function LlmCostRangeToggle({ value, onChange }: Props) {
  const options: { value: Range; label: string }[] = [
    { value: '24h', label: '24시간' },
    { value: '7d', label: '7일' },
    { value: '30d', label: '30일' },
  ];
  return (
    <div className="inline-flex rounded-md border border-slate-200">
      {options.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={`px-3 py-1.5 text-sm ${value === o.value ? 'bg-indigo-600 text-white' : 'bg-white text-slate-700 hover:bg-slate-50'}`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
```

- [ ] **Step 5: typecheck + commit**

```bash
cd frontend && npm run typecheck
git add frontend/src/components/admin/llmCost/
git commit -m "feat(frontend): LLM 비용 KPI/StackedBar/ModelTable/RangeToggle 컴포넌트"
```

---

## Task E4: 페이지 + hook + 라우트 + 사이드바

**Files:**
- Create: `frontend/src/hooks/useAdminLlmCost.ts`
- Create: `frontend/src/pages/admin/AdminLlmCostPage.tsx`
- Modify: `frontend/src/components/layout/AdminSidebar.tsx`
- Modify: 라우터 (`frontend/src/router/...` 또는 `App.tsx`)

- [ ] **Step 1: hook 작성**

```typescript
import { useEffect, useState } from 'react';
import {
  fetchLlmCostKpi,
  fetchLlmCostSeries,
  fetchLlmCostByModule,
  fetchLlmCostByModel,
  type LlmCostKpiResponse,
  type LlmCostSeriesResponse,
  type LlmCostModuleDailyResponse,
  type LlmCostModelSummaryResponse,
} from '@/apis/admin.llmCost.api';

export type Range = '24h' | '7d' | '30d';

export function useAdminLlmCost(range: Range) {
  const [kpi, setKpi] = useState<LlmCostKpiResponse>();
  const [series, setSeries] = useState<LlmCostSeriesResponse>();
  const [byModule, setByModule] = useState<LlmCostModuleDailyResponse[]>([]);
  const [byModel, setByModel] = useState<LlmCostModelSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error>();

  useEffect(() => {
    setLoading(true);
    setError(undefined);
    const dailyRange: '7d' | '30d' = range === '24h' ? '7d' : range;
    Promise.all([
      fetchLlmCostKpi(),
      fetchLlmCostSeries(range),
      fetchLlmCostByModule(dailyRange),
      fetchLlmCostByModel(dailyRange),
    ])
      .then(([k, s, m, mo]) => {
        setKpi(k); setSeries(s); setByModule(m); setByModel(mo);
      })
      .catch((e) => setError(e instanceof Error ? e : new Error(String(e))))
      .finally(() => setLoading(false));
  }, [range]);

  return { kpi, series, byModule, byModel, loading, error };
}
```

- [ ] **Step 2: 페이지 작성**

```typescript
import { useState } from 'react';
import { LlmCostKpiSection } from '@/components/admin/llmCost/LlmCostKpiSection';
import { LlmCostLineChart } from '@/components/admin/llmCost/LlmCostLineChart';
import { LlmCostStackedBar } from '@/components/admin/llmCost/LlmCostStackedBar';
import { LlmCostModelTable } from '@/components/admin/llmCost/LlmCostModelTable';
import { LlmCostRangeToggle } from '@/components/admin/llmCost/LlmCostRangeToggle';
import { useAdminLlmCost, type Range } from '@/hooks/useAdminLlmCost';

export function AdminLlmCostPage() {
  const [range, setRange] = useState<Range>('7d');
  const { kpi, series, byModule, byModel, loading, error } = useAdminLlmCost(range);

  return (
    <div className="space-y-6 p-6">
      <header className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">LLM 비용 대시보드</h1>
        <LlmCostRangeToggle value={range} onChange={setRange} />
      </header>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          데이터를 불러오지 못했습니다: {error.message}
        </div>
      )}
      {loading && <div className="text-sm text-slate-500">로딩 중…</div>}

      {!loading && (
        <>
          <LlmCostKpiSection kpi={kpi} />

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">시간별 비용 추이</h2>
            <LlmCostLineChart points={series?.points ?? []} />
          </section>

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">일자별 모듈 분포</h2>
            <LlmCostStackedBar rows={byModule} />
          </section>

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">모델별 합계</h2>
            <LlmCostModelTable rows={byModel} />
          </section>
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 3: 사이드바 항목 추가**

`AdminSidebar.tsx` 의 메뉴 배열에 다음 추가 (`/admin/qna-cache` 옆):

```typescript
{ to: '/admin/llm-cost', label: 'LLM 비용', icon: ... },
```

(기존 메뉴 구조 — `to`, `label`, `icon` — 를 그대로 따른다. icon 은 기존 메뉴와 일관된 lucide-react 아이콘 사용. 예: `Activity` 또는 `DollarSign`.)

- [ ] **Step 4: 라우터 등록**

기존 `/admin/qna-cache` 가 등록된 위치(`App.tsx` 또는 `routes.tsx`)에 같은 패턴으로:

```tsx
<Route path="/admin/llm-cost" element={<AdminLlmCostPage />} />
```

- [ ] **Step 5: typecheck + 빌드**

```bash
cd frontend && npm run typecheck && npm run build
```

Expected: 둘 다 성공

- [ ] **Step 6: Commit**

```bash
git add frontend/src/hooks/useAdminLlmCost.ts \
        frontend/src/pages/admin/AdminLlmCostPage.tsx \
        frontend/src/components/layout/AdminSidebar.tsx \
        frontend/src/App.tsx
# 라우터 파일 위치에 따라 add 경로 조정
git commit -m "feat(frontend): /admin/llm-cost 페이지 + 라우트 + 사이드바 메뉴"
```

---

# Stage F — 통합 테스트 + 검증

## Task F1: E2E — qna 호출 → bucket 적재

**Files:**
- Create: `backend/src/test/java/com/youthfit/metrics/integration/LlmCostBucketE2ETest.java`

- [ ] **Step 1: 통합 테스트 작성**

```java
package com.youthfit.metrics.integration;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmCostBucket;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class LlmCostBucketE2ETest {

    @Autowired ApplicationEventPublisher publisher;
    @Autowired LlmCostBucketRepository repository;

    @Test
    void 다섯_모듈_각각의_이벤트가_5_row_생성() {
        repository.deleteAll();
        Instant now = Instant.now();
        for (LlmModule m : LlmModule.values()) {
            publisher.publishEvent(new LlmCallRecorded(m, "gpt-4o-mini", 100, 50, now));
        }

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<LlmCostBucket> all = repository.findAll();
            assertThat(all).hasSize(5);
            for (LlmCostBucket b : all) {
                assertThat(b.getCallCount()).isEqualTo(1);
                assertThat(b.getPromptTokens()).isEqualTo(100);
            }
        });
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.metrics.integration.LlmCostBucketE2ETest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/youthfit/metrics/integration/LlmCostBucketE2ETest.java
git commit -m "test(metrics): 5 모듈 동시 이벤트 → 5 bucket 적재 E2E"
```

---

## Task F2: 동시성 테스트 — 같은 bucket 100건 동시 upsert

**Files:**
- Create: `backend/src/test/java/com/youthfit/metrics/integration/LlmCostBucketConcurrencyTest.java`

- [ ] **Step 1: 동시성 테스트 작성**

```java
package com.youthfit.metrics.integration;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmCostBucket;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class LlmCostBucketConcurrencyTest {

    @Autowired ApplicationEventPublisher publisher;
    @Autowired LlmCostBucketRepository repository;

    @Test
    void 같은_시간대_동시_100건_적재해도_1_row_누적() throws InterruptedException {
        repository.deleteAll();
        Instant now = Instant.now();
        int n = 100;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                publisher.publishEvent(new LlmCallRecorded(
                        LlmModule.QNA, "gpt-4o-mini", 1, 1, now));
                latch.countDown();
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            List<LlmCostBucket> all = repository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getCallCount()).isEqualTo(n);
            assertThat(all.get(0).getPromptTokens()).isEqualTo(n);
        });
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.metrics.integration.LlmCostBucketConcurrencyTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/youthfit/metrics/integration/LlmCostBucketConcurrencyTest.java
git commit -m "test(metrics): 동시 100건 upsert → 1 row 누적 (PostgreSQL ON CONFLICT)"
```

---

## Task F3: 전체 빌드 + 테스트 + 프론트 lint

**Files:** 없음 — 검증 단계

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, 모든 신규/기존 테스트 PASS

- [ ] **Step 2: 프론트 typecheck + lint + test**

Run:
```bash
cd frontend && npm run typecheck && npm run lint && npm run test
```
Expected: 0 errors, 0 warnings, 모든 테스트 PASS

- [ ] **Step 3: 로컬 부팅 + 수동 확인**

Run:
```bash
cd backend && ./gradlew bootRun &
cd frontend && npm run dev
```

브라우저에서:
1. 어드민 로그인 → `/admin/llm-cost` 접속 가능
2. "데이터 없음" placeholder (적재 데이터 없음 시) 정상
3. 사이드바 "LLM 비용" 메뉴 활성

수동 적재 후 (정책 가이드 1건 생성 또는 Q&A 1건):
4. KPI / 차트 / 테이블에 1행 등장

- [ ] **Step 4: 부팅 종료**

Run: `pkill -f "bootRun"; pkill -f "vite"` (또는 각 터미널 Ctrl+C)

- [ ] **Step 5: Commit (검증만 한 경우 commit 없음)** — pass

---

# 후속 / 미결 (이번 사이클 외)

- **90일 retention 처리**: 단순 삭제 + 일별 롤업 테이블 신설을 v0.1 검토 (운영 데이터 누적 후 결정)
- **가격표 갱신 절차**: `LlmModelPricing` enum 추가/변경 PR 절차 문서화 (운영 메모로 충분, 별도 runbook 불요)
- **환율 갱신 절차**: 분기별 `application.yml` `youthfit.metrics.llm-cost.usd-to-krw` 운영자 갱신
- **Spec 3 비용 절감 단가 동적화**: `youthfit.qna.cache.estimated-savings-per-hit-usd` 정적 → `LlmCostBucketRepository` 평균 cost/call 기반 동적 산식으로 마이그레이션
- **Anthropic 등 다른 LLM 제공자 추가**: `LlmModule` / `LlmModelPricing` 확장만 필요
- **Micrometer 메트릭 노출**: 시간당 비용 게이지 등 (Grafana 알림용)
- **호출 1건 단위 디버깅 화면**: 적재량 vs ROI 검토 후 v1

---

## 참고 — 인덱스 검증 SQL

운영 환경 적용 후 한 번 실행 권장:

```sql
EXPLAIN ANALYZE
SELECT date_trunc('hour', bucket_at), module, SUM(estimated_cost_usd)
FROM llm_cost_bucket
WHERE bucket_at >= now() - interval '7 days'
GROUP BY 1, 2
ORDER BY 1;
```

`Index Scan` 또는 `Bitmap Index Scan` 이 `idx_llm_cost_bucket_at` 위에서 동작하는지 확인.
