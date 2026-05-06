package com.youthfit.admin.presentation.dto.response;

import com.youthfit.ingestion.domain.model.FailureReason;

import java.time.Instant;

public record IngestionFailureSummaryResponse(
        Long id,
        String source,
        FailureReason failureReason,
        String sourceItemId,
        String errorMessageExcerpt,
        int retryCount,
        Instant createdAt
) {}
