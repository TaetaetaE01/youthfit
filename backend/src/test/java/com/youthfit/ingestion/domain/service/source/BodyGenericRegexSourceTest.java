package com.youthfit.ingestion.domain.service.source;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.domain.service.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.service.LabeledRegexExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BodyGenericRegexSource")
class BodyGenericRegexSourceTest {

    private final BodyGenericRegexSource src = new BodyGenericRegexSource(new LabeledRegexExtractor());

    @Test @DisplayName("사업기간 윈도우는 마스킹되어 후보에서 제외된다")
    void negativeMasked() {
        var ctx = new PeriodExtractionContext("t",
                "[사업기간] 2025.1.1 ~ 2025.12.31\n공지 2026.3.1 ~ 4.30",
                null, null, "ext", List.of());
        assertThat(src.findCandidates(ctx)).singleElement()
                .satisfies(c -> assertThat(c.start()).isEqualTo(LocalDate.of(2026,3,1)));
    }
}
