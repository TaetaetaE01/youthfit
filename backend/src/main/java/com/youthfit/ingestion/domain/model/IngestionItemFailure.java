package com.youthfit.ingestion.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Getter
@Table(
    name = "ingestion_item_failure",
    indexes = {
        @Index(name = "idx_ingestion_item_failure_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_ingestion_item_failure_source_reason_at",
               columnList = "source, failure_reason, created_at DESC")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionItemFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_log_id")
    private Long runLogId;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "source_item_id", length = 120)
    private String sourceItemId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "raw_payload_hash", length = 64)
    private String rawPayloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", nullable = false, length = 30)
    private FailureReason failureReason;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack", columnDefinition = "TEXT")
    private String errorStack;

    @Column(name = "n8n_workflow_name", length = 120)
    private String n8nWorkflowName;

    @Column(name = "n8n_execution_id", length = 64)
    private String n8nExecutionId;

    @Column(name = "n8n_node_name", length = 120)
    private String n8nNodeName;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_retried_at")
    private Instant lastRetriedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static IngestionItemFailure of(
            Long runLogId, String source, String sourceItemId,
            String rawPayload, FailureReason reason, String errorMessage,
            String errorStack,
            String n8nWorkflowName, String n8nExecutionId, String n8nNodeName) {
        IngestionItemFailure f = new IngestionItemFailure();
        f.runLogId = runLogId;
        f.source = source;
        f.sourceItemId = sourceItemId;
        f.rawPayload = rawPayload;
        f.failureReason = reason;
        f.errorMessage = errorMessage;
        f.errorStack = errorStack;
        f.n8nWorkflowName = n8nWorkflowName;
        f.n8nExecutionId = n8nExecutionId;
        f.n8nNodeName = n8nNodeName;
        f.retryCount = 0;
        f.createdAt = Instant.now();
        return f;
    }

    public void markRetried() {
        this.retryCount++;
        this.lastRetriedAt = Instant.now();
    }

    public void redactPayload(String hash) {
        this.rawPayload = null;
        this.rawPayloadHash = hash;
    }

    public boolean isPayloadAvailable() {
        return rawPayload != null && !rawPayload.isBlank();
    }
}
