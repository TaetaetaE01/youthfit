package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.application.dashboard.DashboardThresholds.Ingestion;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestionStaleSignalTest {

    private final IngestionRunLogRepository repo = mock(IngestionRunLogRepository.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO),
            new DashboardThresholds.ScheduledTasks(24)
    );
    private final IngestionStaleSignal signal = new IngestionStaleSignal(repo, thresholds);

    @Test
    void code_is_INGESTION_STALE() {
        assertThat(signal.code()).isEqualTo("INGESTION_STALE");
    }

    @Test
    void empty_when_no_stale_sources() {
        when(repo.staleSources(any())).thenReturn(List.of());
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void high_severity_with_source_names_in_detail() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        when(repo.staleSources(any())).thenReturn(List.of(
                new Object[]{"onlineyouthcenter.kr", now.minusSeconds(10 * 86400)},
                new Object[]{"gov24.go.kr", now.minusSeconds(8 * 86400)}
        ));

        Optional<DashboardSignalResult> r = signal.evaluate(now);

        assertThat(r).isPresent();
        DashboardSignalResult result = r.get();
        assertThat(result.code()).isEqualTo("INGESTION_STALE");
        assertThat(result.severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(result.title()).contains("2");
        assertThat(result.detail()).contains("onlineyouthcenter.kr").contains("gov24.go.kr");
        assertThat(result.deeplink()).isEqualTo("/admin/ingestion?filter=stale");
        assertThat(result.detectedAt()).isEqualTo(now);
    }
}
