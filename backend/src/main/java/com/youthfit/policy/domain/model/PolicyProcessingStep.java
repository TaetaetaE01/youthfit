package com.youthfit.policy.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

@Entity
@Getter
@Table(
        name = "policy_processing_step",
        indexes = {
                @Index(name = "idx_pps_policy_step", columnList = "policy_id, step"),
                @Index(name = "idx_pps_status_finished", columnList = "status, finished_at DESC"),
                @Index(name = "idx_pps_step_status", columnList = "step, status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_pps_policy_step_attempt",
                        columnNames = {"policy_id", "step", "attempt"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyProcessingStep {

    private static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false, length = 20)
    private ProcessingStep step;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProcessingStatus status;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "reason", length = MAX_REASON_LENGTH)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PolicyProcessingStep start(Long policyId, ProcessingStep step, int attempt) {
        PolicyProcessingStep entity = new PolicyProcessingStep();
        entity.policyId = policyId;
        entity.step = step;
        entity.status = ProcessingStatus.IN_PROGRESS;
        entity.attempt = attempt;
        Instant now = Instant.now();
        entity.startedAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void finish(ProcessingStatus newStatus, String reason, String detailJson) {
        this.status = newStatus;
        this.reason = truncate(reason);
        this.detailJson = detailJson;
        Instant now = Instant.now();
        this.finishedAt = now;
        this.durationMs = (int) Math.max(0, Duration.between(this.startedAt, now).toMillis());
        this.updatedAt = now;
    }

    /**
     * 타임아웃으로 강제 마감. 운영 환경에서 listener 가 markFinished 호출에 실패한 (NPE/OOM/kill) 경우
     * {@code PolicyProcessingStepTimeoutScheduler} 가 호출.
     */
    public void markTimedOut() {
        finish(ProcessingStatus.FAILED, "timeout", null);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_REASON_LENGTH ? s : s.substring(0, MAX_REASON_LENGTH);
    }
}
