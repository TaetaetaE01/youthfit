package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.domain.repository.ScheduledTaskRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 스케줄러 미실행 감지 신호.
 *
 * <p>등록된 모든 task 중에서 마지막 성공 시각이 {@code admin.dashboard.scheduled-tasks.stale-hours}
 * 이전이거나 한 번도 성공한 적이 없는 task 가 있으면 HIGH 로 발화한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ScheduledTaskMissedSignal implements DashboardSignal {

    private final ScheduledTaskRunRepository repository;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return "SCHEDULED_TASK_MISSED";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        int staleHours = thresholds.getScheduledTasks().getStaleHours();
        Instant cutoff = now.minus(Duration.ofHours(staleHours));

        List<String> names = repository.findDistinctTaskNames();
        if (names.isEmpty()) {
            return Optional.empty();
        }

        List<String> missed = new ArrayList<>();
        for (String name : names) {
            Optional<Instant> last = repository.findLastSuccessFinishedAt(name);
            if (last.isEmpty() || last.get().isBefore(cutoff)) {
                missed.add(name);
            }
        }
        if (missed.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "스케줄러 " + missed.size() + "개가 " + staleHours + "시간 이상 미실행",
                String.join(", ", missed),
                "/admin",
                now
        ));
    }
}
