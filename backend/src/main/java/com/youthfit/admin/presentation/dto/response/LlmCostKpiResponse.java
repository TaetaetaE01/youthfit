package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;

public record LlmCostKpiResponse(
        BigDecimal todayCostUsd,
        BigDecimal thisWeekCostUsd,
        BigDecimal thisMonthCostUsd,
        long thisMonthCallCount,
        BigDecimal usdToKrwRate,
        BigDecimal lastMonthCostUsd
) {}
