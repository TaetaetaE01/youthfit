package com.youthfit.ingestion.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(
    name = "ingestion_run_log",
    indexes = {
        @Index(name = "idx_ingestion_run_log_received_at", columnList = "received_at DESC"),
        @Index(name = "idx_ingestion_run_log_source_received_at", columnList = "source, received_at DESC")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "received_count", nullable = false)
    private int receivedCount;

    @Column(name = "normalized_success_count", nullable = false)
    private int normalizedSuccessCount;

    @Column(name = "normalized_failure_count", nullable = false)
    private int normalizedFailureCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static IngestionRunLog success(String source, Instant start, Instant end, boolean isDuplicate) {
        IngestionRunLog log = new IngestionRunLog();
        log.source = source;
        log.receivedCount = 1;
        log.normalizedSuccessCount = isDuplicate ? 0 : 1;
        log.normalizedFailureCount = 0;
        log.duplicateCount = isDuplicate ? 1 : 0;
        log.receivedAt = start;
        log.processedAt = end;
        log.durationMs = (int) Math.max(0, java.time.Duration.between(start, end).toMillis());
        log.createdAt = Instant.now();
        return log;
    }

    public static IngestionRunLog failure(String source, Instant start, Instant end) {
        IngestionRunLog log = new IngestionRunLog();
        log.source = source;
        log.receivedCount = 1;
        log.normalizedSuccessCount = 0;
        log.normalizedFailureCount = 1;
        log.duplicateCount = 0;
        log.receivedAt = start;
        log.processedAt = end;
        log.durationMs = (int) Math.max(0, java.time.Duration.between(start, end).toMillis());
        log.createdAt = Instant.now();
        return log;
    }
}
