package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.domain.repository.ScheduledTaskRunRepository;
import com.youthfit.admin.domain.repository.ScheduledTaskRunRepository.TaskFailureSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 스케줄러 실패 감지 신호.
 *
 * <p>최근 24시간 내 실패로 끝난 run 이 1건 이상이면 HIGH 로 발화한다.
 * detail 에는 task 별 실패 횟수가 요약된다.</p>
 */
@Component
@RequiredArgsConstructor
public class ScheduledTaskFailedSignal implements DashboardSignal {

    private final ScheduledTaskRunRepository repository;

    @Override
    public String code() {
        return "SCHEDULED_TASK_FAILED";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        Instant since = now.minus(Duration.ofHours(24));
        long total = repository.countFailedSince(since);
        if (total == 0) {
            return Optional.empty();
        }

        List<TaskFailureSummary> summaries = repository.findFailureSummariesSince(since);
        String detail = summaries.stream()
                .map(s -> s.taskName() + " " + s.failureCount() + "회")
                .collect(Collectors.joining(", "));

        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "스케줄러 실패 " + total + "건 (최근 24시간)",
                detail,
                "/admin",
                now
        ));
    }
}
