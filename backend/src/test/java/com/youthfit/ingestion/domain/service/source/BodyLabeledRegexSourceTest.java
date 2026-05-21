package com.youthfit.ingestion.domain.service.source;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.service.LabeledRegexExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BodyLabeledRegexSource")
class BodyLabeledRegexSourceTest {

    private final BodyLabeledRegexSource src = new BodyLabeledRegexSource(new LabeledRegexExtractor());

    @Test @DisplayName("신청기간 라벨 윈도우의 완전 범위를 후보로 만든다")
    void labeled() {
        var ctx = new PeriodExtractionContext("t",
                "신청기간 2026.3.1 ~ 2026.4.30", null, null, "ext", List.of());
        assertThat(src.findCandidates(ctx)).singleElement()
                .satisfies(c -> assertThat(c.source()).isEqualTo(PeriodSource.BODY_LABELED));
    }

    @Test @DisplayName("사업기간 안의 범위는 후보가 되지 않는다")
    void negativeLabelExcluded() {
        var ctx = new PeriodExtractionContext("t",
                "사업기간 2025.1.1 ~ 2025.12.31", null, null, "ext", List.of());
        assertThat(src.findCandidates(ctx)).isEmpty();
    }
}
