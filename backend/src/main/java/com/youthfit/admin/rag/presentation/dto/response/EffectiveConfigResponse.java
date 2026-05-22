package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.rag.application.dto.result.EffectiveConfig;

public record EffectiveConfigResponse(
        boolean hybridEnabled,
        int topNPerSearch,
        int rrfK,
        double trigramThreshold,
        boolean keywordBoostEnabled,
        int maxKeywords
) {
    public static EffectiveConfigResponse from(EffectiveConfig c) {
        return new EffectiveConfigResponse(
                c.hybridEnabled(), c.topNPerSearch(), c.rrfK(),
                c.trigramThreshold(), c.keywordBoostEnabled(), c.maxKeywords());
    }
}
