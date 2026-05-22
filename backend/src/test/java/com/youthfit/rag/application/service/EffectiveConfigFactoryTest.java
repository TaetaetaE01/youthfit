package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
import com.youthfit.rag.infrastructure.config.KeywordBoostProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EffectiveConfigFactory")
class EffectiveConfigFactoryTest {

    private final HybridSearchProperties baseHybrid =
            new HybridSearchProperties(true, 20, 60, 0.10);
    private final KeywordBoostProperties baseKeyword =
            new KeywordBoostProperties(true, 5, List.of());

    private final EffectiveConfigFactory factory =
            new EffectiveConfigFactory(baseHybrid, baseKeyword);

    @Test
    @DisplayName("overrides 가 null 이면 baseline 값을 그대로 반영한다")
    void nullOverrides_usesBaseline() {
        EffectiveConfig cfg = factory.baseline(null);

        assertThat(cfg.hybridEnabled()).isTrue();
        assertThat(cfg.topNPerSearch()).isEqualTo(20);
        assertThat(cfg.rrfK()).isEqualTo(60);
        assertThat(cfg.trigramThreshold()).isEqualTo(0.10);
        assertThat(cfg.keywordBoostEnabled()).isTrue();
        assertThat(cfg.maxKeywords()).isEqualTo(5);
    }

    @Test
    @DisplayName("overrides 일부 필드만 지정되면 해당 필드만 덮어쓴다")
    void partialOverrides_replacesOnlySpecifiedFields() {
        HybridSearchOverrides ov = new HybridSearchOverrides(
                null, null, 30, null, null, 7);

        EffectiveConfig cfg = factory.baseline(ov);

        assertThat(cfg.rrfK()).isEqualTo(30);
        assertThat(cfg.maxKeywords()).isEqualTo(7);
        assertThat(cfg.topNPerSearch()).isEqualTo(20);   // baseline 그대로
        assertThat(cfg.hybridEnabled()).isTrue();         // baseline 그대로
    }

    @Test
    @DisplayName("overrides 모든 필드 지정 시 baseline 무관하게 override")
    void fullOverrides_replacesAll() {
        HybridSearchOverrides ov = new HybridSearchOverrides(
                false, 30, 30, 0.15, false, 7);

        EffectiveConfig cfg = factory.baseline(ov);

        assertThat(cfg.hybridEnabled()).isFalse();
        assertThat(cfg.topNPerSearch()).isEqualTo(30);
        assertThat(cfg.rrfK()).isEqualTo(30);
        assertThat(cfg.trigramThreshold()).isEqualTo(0.15);
        assertThat(cfg.keywordBoostEnabled()).isFalse();
        assertThat(cfg.maxKeywords()).isEqualTo(7);
    }
}
