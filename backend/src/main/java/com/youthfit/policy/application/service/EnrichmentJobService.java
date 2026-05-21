package com.youthfit.policy.application.service;

import com.youthfit.policy.application.port.ForceEnrichTrigger;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class EnrichmentJobService {

    private static final int RATE_LIMIT_PER_HOUR = 5;

    private final EnrichmentJobRepository jobRepo;
    private final PolicyRepository policyRepo;
    private final ForceEnrichTrigger trigger;
    private final Clock clock;

    public EnrichmentJobService(EnrichmentJobRepository jobRepo,
                                PolicyRepository policyRepo,
                                ForceEnrichTrigger trigger,
                                Clock clock) {
        this.jobRepo = jobRepo;
        this.policyRepo = policyRepo;
        this.trigger = trigger;
        this.clock = clock;
    }

    @Transactional
    public EnrichmentJob create(Long policyId, String requestedBy, List<PolicyReferenceSite> urlsOverride) {
        if (jobRepo.findActiveByPolicyId(policyId).isPresent()) {
            throw new EnrichmentJobConflictException(policyId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        long recent = jobRepo.countRecentByPolicyId(policyId, now.minus(Duration.ofHours(1)));
        if (recent >= RATE_LIMIT_PER_HOUR) {
            throw new EnrichmentJobRateLimitException(policyId, RATE_LIMIT_PER_HOUR);
        }

        Policy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        List<PolicyReferenceSite> urls = (urlsOverride != null && !urlsOverride.isEmpty())
                ? urlsOverride
                : policy.getReferenceSites();
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("Cannot create job: referenceSites is empty");
        }

        int attempt = jobRepo.maxAttemptByPolicyId(policyId) + 1;
        EnrichmentJob job = jobRepo.save(EnrichmentJob.pending(policyId, requestedBy, urls, attempt, now));
        log.info("EnrichmentJob created: jobId={} policyId={} attempt={} urls={} actor={}",
                job.getId(), policyId, attempt, urls.size(), requestedBy);

        try {
            trigger.forceEnrich(job.getId(), policyId, urls);
        } catch (ForceEnrichTrigger.EnrichmentTriggerException e) {
            log.warn("ForceEnrichTrigger failed: jobId={} cause={}", job.getId(), e.getMessage());
            job.markFailed("n8n_unreachable: " + e.getMessage(), LocalDateTime.now(clock));
            jobRepo.save(job);
        }
        return job;
    }

    @Transactional
    public void markRunning(Long jobId) {
        EnrichmentJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        job.markRunning(LocalDateTime.now(clock));
        log.info("EnrichmentJob RUNNING: jobId={}", jobId);
    }

    @Transactional
    public void complete(Long jobId, EnrichmentJobStatus terminal, String errorMessage) {
        if (!terminal.isTerminal()) {
            throw new IllegalArgumentException("complete() requires terminal status: " + terminal);
        }
        EnrichmentJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus().isTerminal()) {
            log.info("EnrichmentJob callback ignored (already terminal): jobId={} current={} attempted={}",
                    jobId, job.getStatus(), terminal);
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (terminal == EnrichmentJobStatus.SUCCESS) {
            job.markSuccess(now);
        } else {
            job.markFailed(errorMessage != null ? errorMessage : "failed", now);
        }
        log.info("EnrichmentJob {}: jobId={} elapsedMs={}",
                terminal, jobId,
                job.getStartedAt() == null ? -1
                        : Duration.between(job.getStartedAt(), now).toMillis());
    }
}
