package com.youthfit.ingestion.domain.repository;

import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IngestionItemFailureRepository extends JpaRepository<IngestionItemFailure, Long> {

    @Query("""
            SELECT f FROM IngestionItemFailure f
             WHERE (:source IS NULL OR f.source = :source)
               AND (:reason IS NULL OR f.failureReason = :reason)
               AND (:from IS NULL OR f.createdAt >= :from)
               AND (:to IS NULL OR f.createdAt < :to)
             ORDER BY f.createdAt DESC
            """)
    Page<IngestionItemFailure> search(
            @Param("source") String source,
            @Param("reason") FailureReason reason,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT f FROM IngestionItemFailure f WHERE f.rawPayload IS NOT NULL AND f.createdAt < :before")
    List<IngestionItemFailure> findPayloadsToRedact(@Param("before") Instant before);

    @Modifying
    @Query("DELETE FROM IngestionItemFailure f WHERE f.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
