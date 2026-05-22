package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmWeeklyBudgetSignalTest {

    private final LlmCostBucketRepository repo = mock(LlmCostBucketRepository.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(new BigDecimal("100000"), new BigDecimal("1.5")),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO)
    );
    private final LlmWeeklyBudgetSignal signal = new LlmWeeklyBudgetSignal(repo, thresholds, new BigDecimal("1350"));

    @Test
    void empty_when_below_budget() {
        // 이번주 누적 USD * 1350 < 100000
        List<Object[]> rows = Collections.singletonList(new Object[]{ new BigDecimal("70"), 0L });
        when(repo.sumBetween(any(), any())).thenReturn(rows);
        // 70 * 1350 = 94500 → 100000 미만
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void high_when_at_or_above_budget() {
        List<Object[]> rows = Collections.singletonList(new Object[]{ new BigDecimal("75"), 0L });
        when(repo.sumBetween(any(), any())).thenReturn(rows);
        // 75 * 1350 = 101250 → 100000 초과
        assertThat(signal.evaluate(Instant.now())).isPresent();
        assertThat(signal.evaluate(Instant.now()).get().severity()).isEqualTo(DashboardSeverity.HIGH);
    }
}
