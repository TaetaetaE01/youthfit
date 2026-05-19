package com.youthfit.admin.presentation.dto.response;

import com.youthfit.ingestion.domain.model.FailureReason;

import java.time.Instant;

public record IngestionFailureDetailResponse(
        Long id,
        String source,
        String sourceItemId,
        FailureReason failureReason,
        String errorMessage,
        String errorStack,
        String rawPayload,
        String rawPayloadHash,
        boolean payloadAvailable,
        int retryCount,
        Instant lastRetriedAt,
        Instant createdAt,
        String n8nWorkflowName,
        String n8nExecutionId,
        String n8nNodeName
) {}
