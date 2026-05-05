package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;

public record QnaCacheLookupKpiResponse(
        BigDecimal todayHitRate,
        BigDecimal yesterdayHitRate,
        BigDecimal sevenDaysAvgSimilarity,
        BigDecimal sevenDaysEstimatedSavingsUsd,
        long sevenDaysHitCount,
        long sevenDaysTotalCount
) {}
