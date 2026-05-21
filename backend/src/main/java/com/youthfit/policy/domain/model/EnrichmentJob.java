package com.youthfit.policy.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "enrichment_job")
public class EnrichmentJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_urls", nullable = false, columnDefinition = "jsonb")
    private List<PolicyReferenceSite> requestedUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnrichmentJobStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected EnrichmentJob() { }

    public static EnrichmentJob pending(Long policyId,
                                        String requestedBy,
                                        List<PolicyReferenceSite> urls,
                                        int attempt,
                                        LocalDateTime now) {
        EnrichmentJob job = new EnrichmentJob();
        job.policyId = policyId;
        job.requestedBy = requestedBy;
        job.requestedUrls = List.copyOf(urls);
        job.status = EnrichmentJobStatus.PENDING;
        job.attempt = attempt;
        job.requestedAt = now;
        return job;
    }

    public void markRunning(LocalDateTime now) {
        if (status != EnrichmentJobStatus.PENDING) {
            return;
        }
        this.status = EnrichmentJobStatus.RUNNING;
        this.startedAt = now;
    }

    public void markSuccess(LocalDateTime now) {
        if (status.isTerminal()) {
            return; // 멱등
        }
        this.status = EnrichmentJobStatus.SUCCESS;
        this.finishedAt = now;
    }

    public void markFailed(String reason, LocalDateTime now) {
        if (status.isTerminal()) {
            return; // 멱등
        }
        this.status = EnrichmentJobStatus.FAILED;
        this.errorMessage = reason;
        this.finishedAt = now;
    }

    public Long getId() { return id; }
    public Long getPolicyId() { return policyId; }
    public String getRequestedBy() { return requestedBy; }
    public List<PolicyReferenceSite> getRequestedUrls() { return requestedUrls; }
    public EnrichmentJobStatus getStatus() { return status; }
    public int getAttempt() { return attempt; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
