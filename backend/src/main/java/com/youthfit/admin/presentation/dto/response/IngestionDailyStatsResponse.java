package com.youthfit.admin.presentation.dto.response;

import java.time.LocalDate;

public record IngestionDailyStatsResponse(
        LocalDate date,
        String source,
        long successCount,
        long failureCount,
        long duplicateCount
) {}
