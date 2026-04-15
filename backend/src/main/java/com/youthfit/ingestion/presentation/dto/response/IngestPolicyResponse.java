package com.youthfit.ingestion.presentation.dto.response;

import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;

import java.util.UUID;

public record IngestPolicyResponse(
        UUID ingestionId,
        String status
) {

    public static IngestPolicyResponse from(IngestPolicyResult result) {
        return new IngestPolicyResponse(result.ingestionId(), result.status());
    }
}
