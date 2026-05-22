package com.youthfit.admin.domain.repository;

import com.youthfit.admin.domain.model.ScheduledTaskRun;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link ScheduledTaskRun} 의 도메인 리포지토리 포트.
 *
 * <p>프레임워크(JPA/Spring) 에 의존하지 않는다. 인프라스트럭처
 * 레이어에서 어댑터를 구현해 주입한다.</p>
 */
public interface ScheduledTaskRunRepository {

    ScheduledTaskRun save(ScheduledTaskRun run);

    /** 최근 성공한 run 의 finishedAt. 없으면 empty. */
    Optional<Instant> findLastSuccessFinishedAt(String taskName);

    /** 등록된 모든 task name (성공·실패 무관, 1회 이상 실행된 적 있는). */
    List<String> findDistinctTaskNames();

    /** since 이후 시점에 실패로 끝난 run 의 개수. */
    long countFailedSince(Instant since);

    /** since 이후 실패 task name + 실패 횟수. detail 빌드용. */
    List<TaskFailureSummary> findFailureSummariesSince(Instant since);

    /** 도메인 인터페이스용 결과 객체. */
    record TaskFailureSummary(String taskName, long failureCount) {}
}
