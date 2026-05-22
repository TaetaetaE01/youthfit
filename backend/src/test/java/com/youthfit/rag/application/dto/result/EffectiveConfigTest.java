package com.youthfit.rag.application.dto.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EffectiveConfig record")
class EffectiveConfigTest {

    @Test
    @DisplayName("모든 필드를 올바르게 저장한다")
    void recordHoldsAllFields() {
        EffectiveConfig cfg = new EffectiveConfig(true, 20, 60, 0.10, true, 5);

        assertThat(cfg.hybridEnabled()).isTrue();
        assertThat(cfg.topNPerSearch()).isEqualTo(20);
        assertThat(cfg.rrfK()).isEqualTo(60);
        assertThat(cfg.trigramThreshold()).isEqualTo(0.10);
        assertThat(cfg.keywordBoostEnabled()).isTrue();
        assertThat(cfg.maxKeywords()).isEqualTo(5);
    }
}
