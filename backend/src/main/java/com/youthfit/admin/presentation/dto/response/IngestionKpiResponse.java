package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;

public record IngestionKpiResponse(
        long yesterdayReceived,
        long yesterdayFailure,
        BigDecimal sevenDayAvgReceivedPerDay,
        BigDecimal sevenDayFailureRate
) {}
