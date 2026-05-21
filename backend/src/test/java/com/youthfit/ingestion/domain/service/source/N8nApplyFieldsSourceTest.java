package com.youthfit.ingestion.domain.service.source;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.domain.service.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("N8nApplyFieldsSource")
class N8nApplyFieldsSourceTest {

    private final N8nApplyFieldsSource src = new N8nApplyFieldsSource();

    @Test @DisplayName("양쪽 모두 존재 → confidence 0.60")
    void bothPresent() {
        var ctx = new PeriodExtractionContext("t", "b",
                LocalDate.of(2026,3,1), LocalDate.of(2026,4,30), "ext", List.of());
        assertThat(src.findCandidates(ctx)).singleElement().satisfies(c -> {
            assertThat(c.confidence()).isEqualTo(0.60);
            assertThat(c.source()).isEqualTo(PeriodSource.N8N);
        });
    }

    @Test @DisplayName("한쪽만 존재 → confidence 0.40")
    void onlyEndPresent() {
        var ctx = new PeriodExtractionContext("t", "b",
                null, LocalDate.of(2026,4,30), "ext", List.of());
        assertThat(src.findCandidates(ctx))
                .singleElement().satisfies(c -> assertThat(c.confidence()).isEqualTo(0.40));
    }

    @Test @DisplayName("양쪽 모두 null → 빈 리스트")
    void bothNull() {
        var ctx = new PeriodExtractionContext("t", "b", null, null, "ext", List.of());
        assertThat(src.findCandidates(ctx)).isEmpty();
    }
}
