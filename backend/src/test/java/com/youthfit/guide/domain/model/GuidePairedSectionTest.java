package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidePairedSectionTest {

    @Test
    void groups가_null이면_예외() {
        assertThatThrownBy(() -> new GuidePairedSection(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groups");
    }

    @Test
    void groups가_비어있으면_예외() {
        assertThatThrownBy(() -> new GuidePairedSection(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어있");
    }

    @Test
    void 라벨_없는_단일_그룹으로_생성() {
        GuideGroup flat = new GuideGroup(null, List.of("만 19~34세", "본인 명의 계약자"));
        GuidePairedSection section = new GuidePairedSection(List.of(flat));

        assertThat(section.groups()).hasSize(1);
        assertThat(section.groups().get(0).label()).isNull();
        assertThat(section.groups().get(0).items()).containsExactly("만 19~34세", "본인 명의 계약자");
    }

    @Test
    void 라벨_있는_여러_그룹으로_생성() {
        GuideGroup g1 = new GuideGroup("일반공급 - 소득 기준", List.of("a", "b"));
        GuideGroup g2 = new GuideGroup("특별공급 - 소득 기준", List.of("c"));
        GuidePairedSection section = new GuidePairedSection(List.of(g1, g2));

        assertThat(section.groups()).hasSize(2);
        assertThat(section.groups().get(0).label()).isEqualTo("일반공급 - 소득 기준");
        assertThat(section.groups().get(1).items()).containsExactly("c");
    }
}
