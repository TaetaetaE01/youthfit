package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidePitfallTest {

    @Test
    void text가_빈문자열이면_예외() {
        assertThatThrownBy(() -> new GuidePitfall("  ", GuideSourceField.SUPPORT_TARGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sourceField가_null이면_예외() {
        assertThatThrownBy(() -> new GuidePitfall("월세 60만원 초과 제외", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 정상_생성() {
        GuidePitfall pitfall = new GuidePitfall("월세 60만원 초과 제외", GuideSourceField.SUPPORT_TARGET);
        assertThat(pitfall.text()).isEqualTo("월세 60만원 초과 제외");
        assertThat(pitfall.sourceField()).isEqualTo(GuideSourceField.SUPPORT_TARGET);
    }
}
