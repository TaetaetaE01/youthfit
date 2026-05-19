package com.youthfit.ingestion.domain.repository;

import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IngestionItemFailureRepository
        extends JpaRepository<IngestionItemFailure, Long>,
                JpaSpecificationExecutor<IngestionItemFailure> {

    @Query("SELECT f FROM IngestionItemFailure f WHERE f.rawPayload IS NOT NULL AND f.createdAt < :before")
    List<IngestionItemFailure> findPayloadsToRedact(@Param("before") Instant before);

    @Modifying
    @Query("DELETE FROM IngestionItemFailure f WHERE f.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
