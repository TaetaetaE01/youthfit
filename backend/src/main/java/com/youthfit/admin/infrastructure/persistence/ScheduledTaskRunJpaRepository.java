package com.youthfit.admin.infrastructure.persistence;

import com.youthfit.admin.domain.model.ScheduledTaskRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * {@link ScheduledTaskRun} 용 Spring Data JPA 인터페이스.
 *
 * <p>도메인 포트 ({@code ScheduledTaskRunRepository}) 의 어댑터에서만
 * 사용된다. 다른 모듈에서 직접 참조하지 않는다.</p>
 */
public interface ScheduledTaskRunJpaRepository extends JpaRepository<ScheduledTaskRun, Long> {

    @Query("""
            SELECT MAX(r.finishedAt)
            FROM ScheduledTaskRun r
            WHERE r.taskName = :name AND r.success = true AND r.finishedAt IS NOT NULL
            """)
    Instant findLastSuccessFinishedAt(@Param("name") String name);

    @Query("SELECT DISTINCT r.taskName FROM ScheduledTaskRun r")
    List<String> findDistinctTaskNames();

    @Query("SELECT COUNT(r) FROM ScheduledTaskRun r WHERE r.success = false AND r.finishedAt >= :since")
    long countFailedSince(@Param("since") Instant since);

    @Query("""
            SELECT r.taskName AS name, COUNT(r) AS cnt
            FROM ScheduledTaskRun r
            WHERE r.success = false AND r.finishedAt >= :since
            GROUP BY r.taskName
            ORDER BY cnt DESC
            """)
    List<Object[]> findFailureGroupsSince(@Param("since") Instant since);
}
