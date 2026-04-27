package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidePairedSectionTest {

    @Test
    void items가_null이면_예외() {
        assertThatThrownBy(() -> new GuidePairedSection(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    @Test
    void items가_비어있으면_예외() {
        assertThatThrownBy(() -> new GuidePairedSection(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어있");
    }

    @Test
    void 정상_생성() {
        GuidePairedSection section = new GuidePairedSection(List.of("만 19~34세", "본인 명의 계약자"));
        assertThat(section.items()).containsExactly("만 19~34세", "본인 명의 계약자");
    }
}
