package com.youthfit.metrics.domain.repository;

import com.youthfit.metrics.domain.model.LlmCostBucket;
import com.youthfit.metrics.domain.model.LlmModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
     * KPI 산출 — 기간별 모듈 무관 합계. 컬럼 순서: cost, calls.
     */
    @Query(value = """
            SELECT COALESCE(SUM(estimated_cost_usd), 0) AS cost,
                   COALESCE(SUM(call_count), 0) AS calls
            FROM llm_cost_bucket
            WHERE bucket_at >= :from AND bucket_at < :to
            """, nativeQuery = true)
    List<Object[]> sumBetween(@Param("from") Instant from, @Param("to") Instant to);

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
