package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.domain.repository.ScheduledTaskRunRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledTaskMissedSignalTest {

    private final ScheduledTaskRunRepository repository = mock(ScheduledTaskRunRepository.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO),
            new DashboardThresholds.ScheduledTasks(24)
    );
    private final ScheduledTaskMissedSignal signal = new ScheduledTaskMissedSignal(repository, thresholds);

    @Test
    void code_is_SCHEDULED_TASK_MISSED() {
        assertThat(signal.code()).isEqualTo("SCHEDULED_TASK_MISSED");
    }

    @Test
    void empty_when_no_tasks_registered() {
        when(repository.findDistinctTaskNames()).thenReturn(List.of());

        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void empty_when_all_tasks_succeeded_within_window() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        when(repository.findDistinctTaskNames()).thenReturn(List.of("taskA", "taskB"));
        // 모든 task 가 1시간 전에 성공함 — cutoff(24시간 전) 보다 최신
        when(repository.findLastSuccessFinishedAt("taskA"))
                .thenReturn(Optional.of(now.minus(Duration.ofHours(1))));
        when(repository.findLastSuccessFinishedAt("taskB"))
                .thenReturn(Optional.of(now.minus(Duration.ofHours(2))));

        assertThat(signal.evaluate(now)).isEmpty();
    }

    @Test
    void high_when_some_tasks_missed() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        when(repository.findDistinctTaskNames()).thenReturn(List.of("staleTask", "freshTask"));
        // staleTask: 25시간 전 마지막 성공 → 미실행으로 판정
        when(repository.findLastSuccessFinishedAt("staleTask"))
                .thenReturn(Optional.of(now.minus(Duration.ofHours(25))));
        // freshTask: 1시간 전 성공 → 정상
        when(repository.findLastSuccessFinishedAt("freshTask"))
                .thenReturn(Optional.of(now.minus(Duration.ofHours(1))));

        Optional<DashboardSignalResult> r = signal.evaluate(now);

        assertThat(r).isPresent();
        DashboardSignalResult result = r.get();
        assertThat(result.code()).isEqualTo("SCHEDULED_TASK_MISSED");
        assertThat(result.severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(result.title()).contains("1").contains("24");
        assertThat(result.detail()).contains("staleTask").doesNotContain("freshTask");
        assertThat(result.deeplink()).isEqualTo("/admin");
        assertThat(result.detectedAt()).isEqualTo(now);
    }

    @Test
    void high_when_task_never_succeeded() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        when(repository.findDistinctTaskNames()).thenReturn(List.of("neverRanSuccessfully"));
        when(repository.findLastSuccessFinishedAt("neverRanSuccessfully"))
                .thenReturn(Optional.empty());

        Optional<DashboardSignalResult> r = signal.evaluate(now);

        assertThat(r).isPresent();
        DashboardSignalResult result = r.get();
        assertThat(result.severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(result.detail()).contains("neverRanSuccessfully");
    }
}
