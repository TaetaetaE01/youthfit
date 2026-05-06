package com.youthfit.metrics.application.event;

import com.youthfit.metrics.domain.model.LlmModule;

import java.time.Instant;

public record LlmCallRecorded(
        LlmModule module,
        String model,
        int promptTokens,
        int completionTokens,
        Instant calledAt
) {}
