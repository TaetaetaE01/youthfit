package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmCostSpikeSignalTest {

    private final LlmCostBucketRepository repo = mock(LlmCostBucketRepository.class);
    private final DashboardThresholds thresholds = thresholds("1.5");
    private final LlmCostSpikeSignal signal = new LlmCostSpikeSignal(repo, thresholds, new BigDecimal("1350"));

    @Test
    void empty_when_yesterday_below_threshold() {
        // 어제 1.0 USD, 직전 7일 합 7.0 → 일평균 1.0. threshold = 1.0 * 1.5 = 1.5. 1.0 <= 1.5 → empty
        List<Object[]> yesterday = Collections.singletonList(new Object[]{ new BigDecimal("1.00"), 0L });
        List<Object[]> lastSeven = Collections.singletonList(new Object[]{ new BigDecimal("7.00"), 0L });
        when(repo.sumBetween(any(), any()))
                .thenReturn(yesterday)
                .thenReturn(lastSeven);
        assertThat(signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"))).isEmpty();
    }

    @Test
    void high_when_yesterday_exceeds_multiplier() {
        List<Object[]> yesterday = Collections.singletonList(new Object[]{ new BigDecimal("2.00"), 0L });
        List<Object[]> lastSeven = Collections.singletonList(new Object[]{ new BigDecimal("7.00"), 0L });
        when(repo.sumBetween(any(), any()))
                .thenReturn(yesterday)   // 어제 2.0 USD
                .thenReturn(lastSeven); // 직전 7일 합 7.0 → 평균 1.0
        Optional<DashboardSignalResult> r = signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"));
        assertThat(r).isPresent();
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(r.get().title()).contains("어제").contains("LLM");
        assertThat(r.get().deeplink()).isEqualTo("/admin/llm-cost");
    }

    private static DashboardThresholds thresholds(String multiplier) {
        return new DashboardThresholds(
                new DashboardThresholds.Llm(new BigDecimal("100000"), new BigDecimal(multiplier)),
                new DashboardThresholds.Ingestion(7),
                new DashboardThresholds.Enrichment(20),
                new DashboardThresholds.Email(BigDecimal.ZERO, 0),
                new DashboardThresholds.QnaCache(BigDecimal.ZERO),
                new DashboardThresholds.PolicyIntake(BigDecimal.ZERO)
        );
    }
}
