package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestionFailureSignalTest {

    private final IngestionItemFailureRepository repo = mock(IngestionItemFailureRepository.class);
    private final IngestionFailureSignal signal = new IngestionFailureSignal(repo);

    @Test
    void code_is_INGESTION_FAILURE() {
        assertThat(signal.code()).isEqualTo("INGESTION_FAILURE");
    }

    @Test
    void returns_empty_when_no_failures_in_last_24h() {
        when(repo.countCreatedAfter(any())).thenReturn(0L);
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void returns_high_severity_when_failures_present() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        when(repo.countCreatedAfter(now.minusSeconds(24 * 3600))).thenReturn(3L);

        Optional<DashboardSignalResult> r = signal.evaluate(now);

        assertThat(r).isPresent();
        DashboardSignalResult result = r.get();
        assertThat(result.code()).isEqualTo("INGESTION_FAILURE");
        assertThat(result.severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(result.title()).contains("3");
        assertThat(result.deeplink()).isEqualTo("/admin/ingestion?tab=failures");
        assertThat(result.detectedAt()).isEqualTo(now);
    }
}
