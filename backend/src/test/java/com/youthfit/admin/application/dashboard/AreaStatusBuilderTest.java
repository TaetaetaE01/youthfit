package com.youthfit.admin.application.dashboard;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AreaStatusBuilderTest {

    private final AreaStatusBuilder builder = new AreaStatusBuilder();

    @Test
    void area_with_no_signal_returns_ok() {
        AreaStatusBuilder.AreaKey ingestion = builder.areas().get(0);
        assertThat(builder.statusFor(ingestion, List.of())).isEqualTo(AreaStatusBuilder.Status.OK);
    }

    @Test
    void single_high_signal_returns_critical() {
        AreaStatusBuilder.AreaKey ingestion = builder.areas().get(0);
        List<DashboardSignalResult> fired = List.of(
                new DashboardSignalResult("INGESTION_FAILURE", DashboardSeverity.HIGH, "t", null, "/d", Instant.now())
        );
        assertThat(builder.statusFor(ingestion, fired)).isEqualTo(AreaStatusBuilder.Status.CRITICAL);
    }

    @Test
    void medium_only_returns_warn() {
        AreaStatusBuilder.AreaKey enrichment = builder.areas().get(1);
        List<DashboardSignalResult> fired = List.of(
                new DashboardSignalResult("ENRICHMENT_BACKLOG", DashboardSeverity.MEDIUM, "t", null, "/d", Instant.now())
        );
        assertThat(builder.statusFor(enrichment, fired)).isEqualTo(AreaStatusBuilder.Status.WARN);
    }

    @Test
    void mixed_signals_take_worst() {
        AreaStatusBuilder.AreaKey enrichment = builder.areas().get(1);
        List<DashboardSignalResult> fired = List.of(
                new DashboardSignalResult("ENRICHMENT_BACKLOG", DashboardSeverity.MEDIUM, "t", null, "/d", Instant.now()),
                new DashboardSignalResult("ENRICHMENT_FAILURE", DashboardSeverity.HIGH, "t", null, "/d", Instant.now())
        );
        assertThat(builder.statusFor(enrichment, fired)).isEqualTo(AreaStatusBuilder.Status.CRITICAL);
    }

    @Test
    void signals_from_other_area_are_ignored() {
        AreaStatusBuilder.AreaKey email = builder.areas().get(3);
        List<DashboardSignalResult> fired = List.of(
                new DashboardSignalResult("INGESTION_FAILURE", DashboardSeverity.HIGH, "t", null, "/d", Instant.now())
        );
        assertThat(builder.statusFor(email, fired)).isEqualTo(AreaStatusBuilder.Status.OK);
    }
}
