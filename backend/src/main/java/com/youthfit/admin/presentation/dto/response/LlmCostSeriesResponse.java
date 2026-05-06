package com.youthfit.admin.presentation.dto.response;

import com.youthfit.metrics.domain.model.LlmModule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record LlmCostSeriesResponse(
        String range,
        List<Point> points
) {
    public record Point(
            Instant at,
            Map<LlmModule, BigDecimal> costByModule
    ) {}
}
