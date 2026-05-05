package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QnaCacheLookupDailyStatsResponse(
        LocalDate date,
        long hitCount,
        long belowThresholdCount,
        long missCount,
        BigDecimal avgSimilarity
) {}
