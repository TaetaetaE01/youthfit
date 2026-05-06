package com.youthfit.admin.presentation.dto.response;

import com.youthfit.ingestion.application.dto.result.RetryResult;

public record IngestionRetryResponse(
        RetryResult.Status status,
        String message,
        Long newFailureId
) {
    public static IngestionRetryResponse from(RetryResult result) {
        return new IngestionRetryResponse(result.status(), result.message(), result.newFailureId());
    }
}
