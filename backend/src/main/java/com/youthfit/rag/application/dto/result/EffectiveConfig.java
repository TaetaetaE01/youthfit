package com.youthfit.rag.application.dto.result;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
import com.youthfit.rag.infrastructure.config.KeywordBoostProperties;

public record EffectiveConfig(
        boolean hybridEnabled,
        int topNPerSearch,
        int rrfK,
        double trigramThreshold,
        boolean keywordBoostEnabled,
        int maxKeywords
) {
    public static EffectiveConfig from(HybridSearchProperties h, KeywordBoostProperties k) {
        return from(h, k, null);
    }

    public static EffectiveConfig from(HybridSearchProperties h,
                                       KeywordBoostProperties k,
                                       HybridSearchOverrides ov) {
        boolean hybrid    = pickBool(ov == null ? null : ov.hybridEnabled(),       h.enabled());
        int     topN      = pickInt (ov == null ? null : ov.topNPerSearch(),       h.topNPerSearch());
        int     rrfK      = pickInt (ov == null ? null : ov.rrfK(),                h.rrfK());
        double  trigramTh = pickDouble(ov == null ? null : ov.trigramThreshold(),  h.trigramThreshold());
        boolean kwBoost   = pickBool(ov == null ? null : ov.keywordBoostEnabled(), k.enabled());
        int     maxKw     = pickInt (ov == null ? null : ov.maxKeywords(),         k.maxKeywords());
        return new EffectiveConfig(hybrid, topN, rrfK, trigramTh, kwBoost, maxKw);
    }

    private static boolean pickBool(Boolean ov, boolean base) { return ov == null ? base : ov; }
    private static int     pickInt (Integer ov, int base)     { return ov == null ? base : ov; }
    private static double  pickDouble(Double ov, double base) { return ov == null ? base : ov; }
}
