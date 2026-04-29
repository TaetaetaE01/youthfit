package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentRefTest {

    @Test
    void givenValidPageRange_whenConstruct_thenOk() {
        AttachmentRef ref = new AttachmentRef(12L, 35, 37);
        assertThat(ref.attachmentId()).isEqualTo(12L);
        assertThat(ref.pageStart()).isEqualTo(35);
        assertThat(ref.pageEnd()).isEqualTo(37);
    }

    @Test
    void givenSinglePage_whenStartEqualsEnd_thenOk() {
        AttachmentRef ref = new AttachmentRef(12L, 35, 35);
        assertThat(ref.pageStart()).isEqualTo(ref.pageEnd());
    }

    @Test
    void givenHwpFallback_whenPagesNull_thenOk() {
        AttachmentRef ref = new AttachmentRef(13L, null, null);
        assertThat(ref.pageStart()).isNull();
        assertThat(ref.pageEnd()).isNull();
    }

    @Test
    void givenAttachmentIdNull_whenConstruct_thenThrows() {
        assertThatThrownBy(() -> new AttachmentRef(null, 35, 37))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void givenOnlyPageStart_whenConstruct_thenThrows() {
        assertThatThrownBy(() -> new AttachmentRef(12L, 35, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("함께 존재");
    }

    @Test
    void givenStartGreaterThanEnd_whenConstruct_thenThrows() {
        assertThatThrownBy(() -> new AttachmentRef(12L, 37, 35))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageStart");
    }
}
