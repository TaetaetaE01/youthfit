package com.youthfit.rag.application.dto.result;

public record EffectiveConfig(
        boolean hybridEnabled,
        int topNPerSearch,
        int rrfK,
        double trigramThreshold,
        boolean keywordBoostEnabled,
        int maxKeywords
) {}
