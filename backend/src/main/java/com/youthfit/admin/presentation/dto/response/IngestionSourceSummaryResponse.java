package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record IngestionSourceSummaryResponse(
        String source,
        Instant lastReceivedAt,
        long sevenDayReceived,
        BigDecimal sevenDayFailureRate,
        boolean stale
) {}
