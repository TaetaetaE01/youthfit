package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuideListSectionTest {

    @Test
    void items가_비어있으면_예외() {
        assertThatThrownBy(() -> new GuideListSection(List.of(), GuideSourceField.ENRICHMENT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    @Test
    void items가_null이면_예외() {
        assertThatThrownBy(() -> new GuideListSection(null, GuideSourceField.ENRICHMENT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sourceField가_null이면_예외() {
        assertThatThrownBy(() -> new GuideListSection(List.of("item"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceField");
    }

    @Test
    void 단일_item과_attachmentRef_null도_정상_생성() {
        GuideListSection s = new GuideListSection(
                List.of("2026-03-01 ~ 2026-05-31"),
                GuideSourceField.ENRICHMENT
        );
        assertThat(s.items()).containsExactly("2026-03-01 ~ 2026-05-31");
        assertThat(s.attachmentRef()).isNull();
    }

    @Test
    void items는_불변_사본() {
        ArrayList<String> mutable = new ArrayList<>();
        mutable.add("a");
        GuideListSection s = new GuideListSection(mutable, GuideSourceField.ENRICHMENT, null);
        mutable.add("b");
        assertThat(s.items()).containsExactly("a");
    }
}
