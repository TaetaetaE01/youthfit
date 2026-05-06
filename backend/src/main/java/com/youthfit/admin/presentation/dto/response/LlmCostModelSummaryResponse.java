package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;

public record LlmCostModelSummaryResponse(
        String model,
        long callCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal totalCostUsd,
        BigDecimal costShare
) {}
