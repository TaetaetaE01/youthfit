package com.youthfit.ingestion.domain.repository;

import com.youthfit.ingestion.domain.model.IngestionRunLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IngestionRunLogRepository extends JpaRepository<IngestionRunLog, Long> {

    /** KPI: 기간 합계. 컬럼 순서: received, success, failure, duplicate. */
    @Query(value = """
            SELECT COALESCE(SUM(received_count), 0) AS received,
                   COALESCE(SUM(normalized_success_count), 0) AS success,
                   COALESCE(SUM(normalized_failure_count), 0) AS failure,
                   COALESCE(SUM(duplicate_count), 0) AS duplicate
            FROM ingestion_run_log
            WHERE received_at >= :from AND received_at < :to
            """, nativeQuery = true)
    List<Object[]> sumBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** 일자별·source 별 stacked bar 용 집계. */
    @Query(value = """
            SELECT (received_at AT TIME ZONE 'Asia/Seoul')::date AS day,
                   source,
                   SUM(normalized_success_count) AS success,
                   SUM(normalized_failure_count) AS failure,
                   SUM(duplicate_count) AS duplicate
            FROM ingestion_run_log
            WHERE received_at >= :from AND received_at < :to
            GROUP BY day, source
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> dailyStats(@Param("from") Instant from, @Param("to") Instant to);

    /** 원천별 마지막 수신 시각 + 7일 합계 + 실패율. */
    @Query(value = """
            SELECT source,
                   MAX(received_at) AS last_received,
                   COALESCE(SUM(received_count) FILTER (WHERE received_at >= :sevenDaysAgo), 0) AS week_received,
                   CASE
                     WHEN COALESCE(SUM(received_count) FILTER (WHERE received_at >= :sevenDaysAgo), 0) = 0 THEN 0.0
                     ELSE COALESCE(SUM(normalized_failure_count) FILTER (WHERE received_at >= :sevenDaysAgo), 0)::numeric
                          / COALESCE(SUM(received_count) FILTER (WHERE received_at >= :sevenDaysAgo), 0)::numeric
                   END AS failure_rate
            FROM ingestion_run_log
            GROUP BY source
            ORDER BY last_received DESC
            """, nativeQuery = true)
    List<Object[]> sourceSummaries(@Param("sevenDaysAgo") Instant sevenDaysAgo);

    /** 24h (또는 임계) 미수신 source 식별. */
    @Query(value = """
            SELECT source, MAX(received_at) AS last_received
            FROM ingestion_run_log
            GROUP BY source
            HAVING MAX(received_at) < :threshold
            """, nativeQuery = true)
    List<Object[]> staleSources(@Param("threshold") Instant threshold);
}
