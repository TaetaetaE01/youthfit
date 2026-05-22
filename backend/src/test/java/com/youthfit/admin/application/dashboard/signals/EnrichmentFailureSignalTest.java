package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnrichmentFailureSignalTest {

    private final EnrichmentJobRepository repo = mock(EnrichmentJobRepository.class);
    private final EnrichmentFailureSignal signal = new EnrichmentFailureSignal(repo);

    @Test
    void code_is_ENRICHMENT_FAILURE() {
        assertThat(signal.code()).isEqualTo("ENRICHMENT_FAILURE");
    }

    @Test
    void returns_empty_when_no_failures_in_last_24h() {
        when(repo.countFailedSince(any())).thenReturn(0L);
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void returns_high_severity_when_at_least_one_failure() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        when(repo.countFailedSince(now.minusSeconds(24 * 3600))).thenReturn(2L);

        Optional<DashboardSignalResult> r = signal.evaluate(now);

        assertThat(r).isPresent();
        DashboardSignalResult result = r.get();
        assertThat(result.code()).isEqualTo("ENRICHMENT_FAILURE");
        assertThat(result.severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(result.title()).contains("2");
        assertThat(result.deeplink()).isEqualTo("/admin/enrichment?filter=failed");
        assertThat(result.detectedAt()).isEqualTo(now);
    }
}
