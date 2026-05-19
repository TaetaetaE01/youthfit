package com.youthfit.qna.domain.repository;

import com.youthfit.qna.domain.model.LookupResultType;
import com.youthfit.qna.domain.model.QnaCacheLookupLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface QnaCacheLookupLogRepository extends JpaRepository<QnaCacheLookupLog, Long> {

    @Query("""
        SELECT l FROM QnaCacheLookupLog l
        WHERE (:result IS NULL OR l.result = :result)
          AND (:policyId IS NULL OR l.policyId = :policyId)
          AND (:from IS NULL OR l.lookedUpAt >= :from)
          AND (:to IS NULL OR l.lookedUpAt <= :to)
        ORDER BY l.lookedUpAt DESC
        """)
    Page<QnaCacheLookupLog> search(
            @Param("result") LookupResultType result,
            @Param("policyId") Long policyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("""
        SELECT l FROM QnaCacheLookupLog l
        WHERE (:result IS NULL OR l.result = :result)
          AND (:policyId IS NULL OR l.policyId = :policyId)
          AND (:from IS NULL OR l.lookedUpAt >= :from)
          AND (:to IS NULL OR l.lookedUpAt <= :to)
        ORDER BY l.lookedUpAt DESC
        """)
    List<QnaCacheLookupLog> exportFiltered(
            @Param("result") LookupResultType result,
            @Param("policyId") Long policyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query(value = """
        SELECT to_char(date_trunc('day', looked_up_at), 'YYYY-MM-DD') AS day,
               COUNT(*) FILTER (WHERE result = 'HIT')             AS hit_count,
               COUNT(*) FILTER (WHERE result = 'BELOW_THRESHOLD') AS below_count,
               COUNT(*) FILTER (WHERE result = 'MISS')            AS miss_count,
               COALESCE(AVG(similarity_score) FILTER (WHERE result = 'HIT'), 0) AS avg_sim
        FROM qna_cache_lookup_log
        WHERE looked_up_at BETWEEN :from AND :to
        GROUP BY 1
        ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> aggregateDaily(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    @Query(value = """
        SELECT
          COUNT(*) FILTER (WHERE looked_up_at >= :today)                                      AS today_total,
          COUNT(*) FILTER (WHERE looked_up_at >= :today AND result = 'HIT')                   AS today_hits,
          COUNT(*) FILTER (WHERE looked_up_at >= :yesterday AND looked_up_at < :today)        AS yest_total,
          COUNT(*) FILTER (WHERE looked_up_at >= :yesterday AND looked_up_at < :today AND result = 'HIT') AS yest_hits,
          COUNT(*) FILTER (WHERE looked_up_at >= :sevenDaysAgo AND result = 'HIT')            AS seven_hits,
          COALESCE(AVG(similarity_score) FILTER (WHERE looked_up_at >= :sevenDaysAgo AND result = 'HIT'), 0) AS seven_avg_sim,
          COUNT(*) FILTER (WHERE looked_up_at >= :sevenDaysAgo)                               AS seven_total
        FROM qna_cache_lookup_log
        """, nativeQuery = true)
    List<Object[]> aggregateKpi(@Param("today") LocalDateTime today,
                                @Param("yesterday") LocalDateTime yesterday,
                                @Param("sevenDaysAgo") LocalDateTime sevenDaysAgo);

    @Modifying
    @Query("DELETE FROM QnaCacheLookupLog l WHERE l.lookedUpAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
