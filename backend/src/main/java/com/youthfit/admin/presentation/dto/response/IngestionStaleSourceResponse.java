package com.youthfit.admin.presentation.dto.response;

import java.time.Instant;

public record IngestionStaleSourceResponse(
        String source,
        Instant lastReceivedAt,
        long hoursSinceLastReceived
) {}
