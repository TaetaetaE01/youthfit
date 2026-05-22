package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailFailureSignalTest {

    private final EmailSendAttemptRepository repository = mock(EmailSendAttemptRepository.class);

    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(new BigDecimal("0.05"), 10),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO),
            new DashboardThresholds.ScheduledTasks(24)
    );

    private final EmailFailureSignal signal = new EmailFailureSignal(repository, thresholds);

    @Test
    void code_is_EMAIL_FAILURE() {
        assertThat(signal.code()).isEqualTo("EMAIL_FAILURE");
    }

    @Test
    void returns_empty_when_no_emails_in_last_24h() {
        when(repository.countSentSince(any(LocalDateTime.class))).thenReturn(0L);
        when(repository.countByStatusSince(any(LocalDateTime.class), anyCollection())).thenReturn(0L);

        Optional<DashboardSignalResult> result = signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"));

        assertThat(result).isEmpty();
    }

    @Test
    void returns_high_severity_when_failure_rate_exceeds_threshold() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        // 6 failures out of 100 -> 6% > 5%
        when(repository.countSentSince(any(LocalDateTime.class))).thenReturn(100L);
        when(repository.countByStatusSince(any(LocalDateTime.class), anyCollection())).thenReturn(6L);

        Optional<DashboardSignalResult> result = signal.evaluate(now);

        assertThat(result).isPresent();
        DashboardSignalResult r = result.get();
        assertThat(r.code()).isEqualTo("EMAIL_FAILURE");
        assertThat(r.severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(r.title()).contains("6").contains("100").contains("6.0%");
        assertThat(r.deeplink()).isEqualTo("/admin/email?filter=failed");
        assertThat(r.detectedAt()).isEqualTo(now);
    }

    @Test
    void returns_high_severity_when_absolute_count_exceeds_threshold_even_with_low_rate() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        // 11 failures out of 10000 -> 0.11% < 5%, but absolute > 10
        when(repository.countSentSince(any(LocalDateTime.class))).thenReturn(10_000L);
        when(repository.countByStatusSince(any(LocalDateTime.class), anyCollection())).thenReturn(11L);

        Optional<DashboardSignalResult> result = signal.evaluate(now);

        assertThat(result).isPresent();
        DashboardSignalResult r = result.get();
        assertThat(r.severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(r.title()).contains("11").contains("10000");
    }

    @Test
    void returns_empty_when_both_rate_and_count_below_thresholds() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        // 3 failures out of 1000 -> 0.3% < 5%, absolute 3 <= 10
        when(repository.countSentSince(any(LocalDateTime.class))).thenReturn(1_000L);
        when(repository.countByStatusSince(any(LocalDateTime.class), anyCollection())).thenReturn(3L);

        Optional<DashboardSignalResult> result = signal.evaluate(now);

        assertThat(result).isEmpty();
    }
}
