package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
import com.youthfit.rag.infrastructure.config.KeywordBoostProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * baseline yml 설정(HybridSearchProperties, KeywordBoostProperties)과
 * 어드민 오버라이드(HybridSearchOverrides)를 합성해 EffectiveConfig 를 생성한다.
 * <p>
 * 인프라 설정 타입을 아는 것이 허용되는 application 서비스 레이어에 위치하며,
 * 순수 결과 레코드인 EffectiveConfig 는 인프라 의존을 갖지 않는다.
 */
@Component
@RequiredArgsConstructor
public class EffectiveConfigFactory {

    private final HybridSearchProperties hybrid;
    private final KeywordBoostProperties keyword;

    public EffectiveConfig baseline() {
        return baseline(null);
    }

    public EffectiveConfig baseline(HybridSearchOverrides ov) {
        boolean hybridEnabled     = pickBool  (ov == null ? null : ov.hybridEnabled(),       hybrid.enabled());
        int     topNPerSearch     = pickInt   (ov == null ? null : ov.topNPerSearch(),       hybrid.topNPerSearch());
        int     rrfK              = pickInt   (ov == null ? null : ov.rrfK(),                hybrid.rrfK());
        double  trigramThreshold  = pickDouble(ov == null ? null : ov.trigramThreshold(),    hybrid.trigramThreshold());
        boolean keywordBoostEnabled = pickBool(ov == null ? null : ov.keywordBoostEnabled(), keyword.enabled());
        int     maxKeywords       = pickInt   (ov == null ? null : ov.maxKeywords(),         keyword.maxKeywords());
        return new EffectiveConfig(hybridEnabled, topNPerSearch, rrfK, trigramThreshold, keywordBoostEnabled, maxKeywords);
    }

    private static boolean pickBool  (Boolean ov, boolean base) { return ov == null ? base : ov; }
    private static int     pickInt   (Integer ov, int base)     { return ov == null ? base : ov; }
    private static double  pickDouble(Double ov, double base)   { return ov == null ? base : ov; }
}
