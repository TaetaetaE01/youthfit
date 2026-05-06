package com.youthfit.metrics.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum LlmModelPricing {
    GPT_4O_MINI("gpt-4o-mini", new BigDecimal("0.000150"), new BigDecimal("0.000600")),
    GPT_4O("gpt-4o", new BigDecimal("0.0025"), new BigDecimal("0.010")),
    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small", new BigDecimal("0.00002"), BigDecimal.ZERO),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large", new BigDecimal("0.00013"), BigDecimal.ZERO),
    UNKNOWN("__unknown__", BigDecimal.ZERO, BigDecimal.ZERO);

    private static final BigDecimal PER_1K = BigDecimal.valueOf(1000);

    private final String modelId;
    private final BigDecimal inputPer1K;
    private final BigDecimal outputPer1K;

    LlmModelPricing(String modelId, BigDecimal inputPer1K, BigDecimal outputPer1K) {
        this.modelId = modelId;
        this.inputPer1K = inputPer1K;
        this.outputPer1K = outputPer1K;
    }

    public static LlmModelPricing of(String model) {
        if (model == null) return UNKNOWN;
        for (LlmModelPricing p : values()) {
            if (p.modelId.equals(model)) return p;
        }
        return UNKNOWN;
    }

    public BigDecimal calculate(long promptTokens, long completionTokens) {
        BigDecimal input = inputPer1K.multiply(BigDecimal.valueOf(promptTokens)).divide(PER_1K, 6, RoundingMode.HALF_UP);
        BigDecimal output = outputPer1K.multiply(BigDecimal.valueOf(completionTokens)).divide(PER_1K, 6, RoundingMode.HALF_UP);
        return input.add(output);
    }

    public String modelId() {
        return modelId;
    }
}
