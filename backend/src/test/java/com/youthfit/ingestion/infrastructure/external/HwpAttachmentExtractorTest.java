package com.youthfit.ingestion.infrastructure.external;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HwpAttachmentExtractorTest {

    private final HwpAttachmentExtractor sut = new HwpAttachmentExtractor();

    @Test
    void supports_는_HWP_관련_mediaType만_true() {
        assertThat(sut.supports("application/x-hwp")).isTrue();
        assertThat(sut.supports("application/haansofthwp")).isTrue();
        assertThat(sut.supports("application/vnd.hancom.hwp")).isTrue();
    }

    @Test
    void supports_는_PDF_등은_false() {
        assertThat(sut.supports("application/pdf")).isFalse();
        assertThat(sut.supports("text/plain")).isFalse();
        assertThat(sut.supports(null)).isFalse();
    }
}
