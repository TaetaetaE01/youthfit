package com.youthfit.admin.application.dashboard;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSignalEvaluatorTest {

    @Test
    void collects_all_present_results_and_sorts_high_first() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        DashboardSignal med = stub("A", Optional.of(result("A", DashboardSeverity.MEDIUM, now.minusSeconds(60))));
        DashboardSignal high = stub("B", Optional.of(result("B", DashboardSeverity.HIGH, now.minusSeconds(120))));
        DashboardSignal none = stub("C", Optional.empty());

        DashboardSignalEvaluator evaluator = new DashboardSignalEvaluator(List.of(med, high, none));

        List<DashboardSignalResult> results = evaluator.evaluateAll(now);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).code()).isEqualTo("B");   // HIGH first
        assertThat(results.get(1).code()).isEqualTo("A");
    }

    @Test
    void same_severity_sorted_by_detectedAt_desc() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        DashboardSignal older = stub("A", Optional.of(result("A", DashboardSeverity.HIGH, now.minusSeconds(300))));
        DashboardSignal newer = stub("B", Optional.of(result("B", DashboardSeverity.HIGH, now.minusSeconds(60))));

        DashboardSignalEvaluator evaluator = new DashboardSignalEvaluator(List.of(older, newer));

        List<DashboardSignalResult> results = evaluator.evaluateAll(now);

        assertThat(results.get(0).code()).isEqualTo("B");   // newer first
    }

    @Test
    void thrown_signal_is_isolated_others_still_evaluated() {
        Instant now = Instant.now();
        DashboardSignal broken = new DashboardSignal() {
            @Override public String code() { return "BROKEN"; }
            @Override public Optional<DashboardSignalResult> evaluate(Instant now) {
                throw new RuntimeException("boom");
            }
        };
        DashboardSignal ok = stub("OK", Optional.of(result("OK", DashboardSeverity.HIGH, now)));

        DashboardSignalEvaluator evaluator = new DashboardSignalEvaluator(List.of(broken, ok));

        List<DashboardSignalResult> results = evaluator.evaluateAll(now);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).code()).isEqualTo("OK");
    }

    private static DashboardSignal stub(String code, Optional<DashboardSignalResult> r) {
        return new DashboardSignal() {
            @Override public String code() { return code; }
            @Override public Optional<DashboardSignalResult> evaluate(Instant now) { return r; }
        };
    }

    private static DashboardSignalResult result(String code, DashboardSeverity sev, Instant at) {
        return new DashboardSignalResult(code, sev, "t", null, "/d", at);
    }
}
