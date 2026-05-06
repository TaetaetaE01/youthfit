package com.youthfit.admin.presentation.dto.response;

import com.youthfit.metrics.domain.model.LlmModule;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LlmCostModuleDailyResponse(
        LocalDate date,
        LlmModule module,
        BigDecimal totalCostUsd,
        long callCount
) {}
