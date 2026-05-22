package com.youthfit.admin.infrastructure.persistence;

import com.youthfit.admin.domain.model.ScheduledTaskRun;
import com.youthfit.admin.domain.repository.ScheduledTaskRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link ScheduledTaskRunRepository} 의 Spring Data JPA 어댑터.
 *
 * <p>도메인 포트와 {@link ScheduledTaskRunJpaRepository} 사이의 변환을 담당한다.
 * 트랜잭션은 호출하는 application 서비스(AOP recorder 포함)에 위임한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class ScheduledTaskRunRepositoryImpl implements ScheduledTaskRunRepository {

    private final ScheduledTaskRunJpaRepository jpa;

    @Override
    public ScheduledTaskRun save(ScheduledTaskRun run) {
        return jpa.save(run);
    }

    @Override
    public Optional<Instant> findLastSuccessFinishedAt(String taskName) {
        return Optional.ofNullable(jpa.findLastSuccessFinishedAt(taskName));
    }

    @Override
    public List<String> findDistinctTaskNames() {
        return jpa.findDistinctTaskNames();
    }

    @Override
    public long countFailedSince(Instant since) {
        return jpa.countFailedSince(since);
    }

    @Override
    public List<TaskFailureSummary> findFailureSummariesSince(Instant since) {
        List<Object[]> rows = jpa.findFailureGroupsSince(since);
        List<TaskFailureSummary> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String name = (String) row[0];
            long cnt = ((Number) row[1]).longValue();
            result.add(new TaskFailureSummary(name, cnt));
        }
        return result;
    }
}
