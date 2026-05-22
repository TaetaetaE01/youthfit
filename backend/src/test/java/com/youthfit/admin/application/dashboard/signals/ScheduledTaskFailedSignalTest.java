package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.domain.repository.ScheduledTaskRunRepository;
import com.youthfit.admin.domain.repository.ScheduledTaskRunRepository.TaskFailureSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledTaskFailedSignalTest {

    private final ScheduledTaskRunRepository repository = mock(ScheduledTaskRunRepository.class);
    private final ScheduledTaskFailedSignal signal = new ScheduledTaskFailedSignal(repository);

    @Test
    void code_is_SCHEDULED_TASK_FAILED() {
        assertThat(signal.code()).isEqualTo("SCHEDULED_TASK_FAILED");
    }

    @Test
    void empty_when_no_failures() {
        when(repository.countFailedSince(any())).thenReturn(0L);

        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void high_when_failures_present_includes_summary() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        when(repository.countFailedSince(any())).thenReturn(3L);
        when(repository.findFailureSummariesSince(any())).thenReturn(List.of(
                new TaskFailureSummary("ingestionScheduler", 2L),
                new TaskFailureSummary("attachmentScheduler", 1L)
        ));

        Optional<DashboardSignalResult> r = signal.evaluate(now);

        assertThat(r).isPresent();
        DashboardSignalResult result = r.get();
        assertThat(result.code()).isEqualTo("SCHEDULED_TASK_FAILED");
        assertThat(result.severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(result.title()).contains("3");
        assertThat(result.detail())
                .contains("ingestionScheduler")
                .contains("2회")
                .contains("attachmentScheduler")
                .contains("1회");
        assertThat(result.deeplink()).isEqualTo("/admin");
        assertThat(result.detectedAt()).isEqualTo(now);
    }
}
