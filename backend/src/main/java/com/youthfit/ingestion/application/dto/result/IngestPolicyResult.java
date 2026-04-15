package com.youthfit.ingestion.application.dto.result;

import java.util.UUID;

public record IngestPolicyResult(
        UUID ingestionId,
        String status
) {}
