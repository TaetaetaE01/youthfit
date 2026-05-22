package com.youthfit.admin.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 스케줄러(@Scheduled) 메서드 한 회 실행에 대한 라이프사이클 기록.
 *
 * <p>어드민 대시보드의 배치 상태 모니터링(stalled / failing)에 사용된다.
 * 시작 시점에 한 행을 insert 하고, 종료 시점(성공/실패)에 동일 행을
 * {@code markSucceeded} / {@code markFailed} 로 업데이트한다.
 * {@code finishedAt == null} 인 행은 실행 중이거나 종료 기록 없이
 * 크래시한 상태로 해석한다.</p>
 */
@Entity
@Table(name = "scheduled_task_run", indexes = {
        @Index(name = "idx_str_task_name_started", columnList = "task_name, started_at"),
        @Index(name = "idx_str_started_at", columnList = "started_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledTaskRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** null = 실행 중이거나 기록 없이 크래시. */
    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    private ScheduledTaskRun(String taskName, Instant startedAt) {
        this.taskName = taskName;
        this.startedAt = startedAt;
        this.success = false;
    }

    public static ScheduledTaskRun started(String taskName, Instant at) {
        return new ScheduledTaskRun(taskName, at);
    }

    public void markSucceeded(Instant at) {
        this.finishedAt = at;
        this.success = true;
        this.errorMessage = null;
    }

    public void markFailed(Instant at, String message) {
        this.finishedAt = at;
        this.success = false;
        this.errorMessage = truncate(message, 1000);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
