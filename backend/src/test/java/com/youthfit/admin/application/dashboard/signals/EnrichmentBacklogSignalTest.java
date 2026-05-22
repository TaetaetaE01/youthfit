package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult;
import com.youthfit.policy.application.service.AdminEnrichmentQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnrichmentBacklogSignalTest {

    private final AdminEnrichmentQueryService queryService = mock(AdminEnrichmentQueryService.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO),
            new DashboardThresholds.ScheduledTasks(24)
    );
    private final EnrichmentBacklogSignal signal = new EnrichmentBacklogSignal(queryService, thresholds);

    @Test
    void code_is_ENRICHMENT_BACKLOG() {
        assertThat(signal.code()).isEqualTo("ENRICHMENT_BACKLOG");
    }

    @Test
    void returns_empty_when_below_threshold() {
        when(queryService.findReviewSummary()).thenReturn(summary(19));
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void returns_medium_when_at_threshold() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        when(queryService.findReviewSummary()).thenReturn(summary(20));

        Optional<DashboardSignalResult> r = signal.evaluate(now);

        assertThat(r).isPresent();
        DashboardSignalResult result = r.get();
        assertThat(result.code()).isEqualTo("ENRICHMENT_BACKLOG");
        assertThat(result.severity()).isEqualTo(DashboardSeverity.MEDIUM);
        assertThat(result.title()).contains("20");
        assertThat(result.deeplink()).isEqualTo("/admin/enrichment");
        assertThat(result.detectedAt()).isEqualTo(now);
    }

    @Test
    void returns_medium_when_above_threshold() {
        when(queryService.findReviewSummary()).thenReturn(summary(57));

        Optional<DashboardSignalResult> r = signal.evaluate(Instant.now());

        assertThat(r).isPresent();
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.MEDIUM);
        assertThat(r.get().title()).contains("57");
    }

    private EnrichmentReviewSummaryResult summary(long needsReview) {
        return new EnrichmentReviewSummaryResult(needsReview, needsReview, Map.of(), Map.of());
    }
}
