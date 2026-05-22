package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.infrastructure.persistence.DashboardPolicyQueryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyIntakeStallSignalTest {

    private final DashboardPolicyQueryRepository repo = mock(DashboardPolicyQueryRepository.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(new BigDecimal("0.3")),
            new DashboardThresholds.ScheduledTasks(24)
    );
    private final PolicyIntakeStallSignal signal = new PolicyIntakeStallSignal(repo, thresholds);

    @Test
    void empty_when_today_meets_baseline() {
        // 오늘 5, 직전 7일 70 → 일평균 10. 5 / 10 = 0.5 >= 0.3 → 정상
        when(repo.countPolicyCreatedBetween(any(), any()))
                .thenReturn(5L)    // 오늘
                .thenReturn(70L);  // 직전 7일
        assertThat(signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"))).isEmpty();
    }

    @Test
    void medium_when_today_below_ratio() {
        // 오늘 2, 직전 7일 70 → 일평균 10. 2/10=0.2 < 0.3
        when(repo.countPolicyCreatedBetween(any(), any()))
                .thenReturn(2L)
                .thenReturn(70L);
        Optional<DashboardSignalResult> r = signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"));
        assertThat(r).isPresent();
        assertThat(r.get().code()).isEqualTo("POLICY_INTAKE_STALL");
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.MEDIUM);
        assertThat(r.get().title()).contains("오늘 신규 정책 2건").contains("7일 평균").contains("저조");
        assertThat(r.get().deeplink()).isEqualTo("/admin/ingestion");
    }

    @Test
    void empty_when_no_baseline() {
        // 직전 7일 0이면 비교 불가 → 정상으로 간주
        when(repo.countPolicyCreatedBetween(any(), any()))
                .thenReturn(0L)
                .thenReturn(0L);
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }
}
