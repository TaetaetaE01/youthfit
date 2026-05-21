package com.youthfit.ingestion.domain.service.source;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.service.LabeledRegexExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AttachmentLabeledRegexSource")
class AttachmentLabeledRegexSourceTest {

    private final AttachmentLabeledRegexSource src = new AttachmentLabeledRegexSource(new LabeledRegexExtractor());

    @Test @DisplayName("여러 첨부 텍스트의 라벨 후보를 모두 모은다")
    void multiAttachments() {
        var ctx = new PeriodExtractionContext("t", "", null, null, "ext", List.of(
                "신청기간 2026.3.1 ~ 4.30",
                "신청기간 2026.5.1 ~ 5.31"
        ));
        assertThat(src.findCandidates(ctx)).hasSize(2)
                .allSatisfy(c -> assertThat(c.source()).isEqualTo(PeriodSource.ATTACHMENT_LABELED));
    }

    @Test @DisplayName("첨부 LABELED 의 confidence 는 BODY_LABELED 보다 0.10 낮다 (0.75)")
    void attachmentConfidence() {
        var ctx = new PeriodExtractionContext("t", "", null, null, "ext",
                List.of("신청기간 2026.3.1 ~ 2026.4.30"));
        assertThat(src.findCandidates(ctx)).singleElement()
                .satisfies(c -> assertThat(c.confidence()).isEqualTo(0.75));
    }
}
