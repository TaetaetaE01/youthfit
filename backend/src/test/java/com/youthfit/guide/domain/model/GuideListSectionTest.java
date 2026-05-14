package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuideListSectionTest {

    @Test
    @DisplayName("items 가 비어있으면 생성 실패")
    void items_empty_then_fail() {
        assertThatThrownBy(() -> new GuideListSection(List.of(), GuideSourceField.ENRICHMENT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    @Test
    @DisplayName("items null 이면 생성 실패")
    void items_null_then_fail() {
        assertThatThrownBy(() -> new GuideListSection(null, GuideSourceField.ENRICHMENT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sourceField null 이면 생성 실패")
    void sourceField_null_then_fail() {
        assertThatThrownBy(() -> new GuideListSection(List.of("item"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceField");
    }

    @Test
    @DisplayName("items 1개 + attachmentRef null 도 정상 생성")
    void single_item_no_attachment_then_ok() {
        GuideListSection s = new GuideListSection(
                List.of("2026-03-01 ~ 2026-05-31"),
                GuideSourceField.ENRICHMENT
        );
        assertThat(s.items()).containsExactly("2026-03-01 ~ 2026-05-31");
        assertThat(s.attachmentRef()).isNull();
    }

    @Test
    @DisplayName("items 는 불변 사본")
    void items_defensive_copy() {
        java.util.ArrayList<String> mutable = new java.util.ArrayList<>();
        mutable.add("a");
        GuideListSection s = new GuideListSection(mutable, GuideSourceField.ENRICHMENT, null);
        mutable.add("b");
        assertThat(s.items()).containsExactly("a");
    }
}
