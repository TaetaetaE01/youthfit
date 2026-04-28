package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionDispatcherTest {

    @Test
    void dispatch_는_supports_가_true_인_첫_extractor를_사용() {
        AttachmentExtractor a = new StubExtractor("application/pdf", "from-A");
        AttachmentExtractor b = new StubExtractor("application/pdf", "from-B");
        ExtractionDispatcher sut = new ExtractionDispatcher(List.of(a, b));

        ExtractionResult r = sut.dispatch(new ByteArrayInputStream(new byte[0]), 0, "application/pdf");

        assertThat(r).isInstanceOf(ExtractionResult.Success.class);
        assertThat(((ExtractionResult.Success) r).text()).isEqualTo("from-A");
    }

    @Test
    void dispatch_는_지원하는_extractor가_없으면_UNSUPPORTED_MIME_skip() {
        ExtractionDispatcher sut = new ExtractionDispatcher(List.of(
                new StubExtractor("application/pdf", "x")
        ));
        ExtractionResult r = sut.dispatch(new ByteArrayInputStream(new byte[0]), 0, "image/png");
        assertThat(r).isInstanceOf(ExtractionResult.Skipped.class);
        assertThat(((ExtractionResult.Skipped) r).reason())
                .isEqualTo(com.youthfit.policy.domain.model.SkipReason.UNSUPPORTED_MIME);
    }

    private record StubExtractor(String mime, String returnText) implements AttachmentExtractor {
        @Override public boolean supports(String mediaType) { return mime.equalsIgnoreCase(mediaType); }
        @Override public ExtractionResult extract(java.io.InputStream s, long size) {
            return ExtractionResult.success(returnText);
        }
    }
}
