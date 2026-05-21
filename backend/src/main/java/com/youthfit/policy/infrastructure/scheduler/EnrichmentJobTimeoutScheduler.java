package com.youthfit.policy.infrastructure.scheduler;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class EnrichmentJobTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentJobTimeoutScheduler.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final EnrichmentJobRepository repo;
    private final Clock clock;

    public EnrichmentJobTimeoutScheduler(EnrichmentJobRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${enrichment.timeout.fixed-delay-ms:60000}")
    @Transactional
    public void expireStaleJobs() {
        LocalDateTime threshold = LocalDateTime.now(clock).minus(TIMEOUT);
        List<EnrichmentJob> stale = repo.findActiveStaleBefore(threshold);
        if (stale.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now(clock);
        for (EnrichmentJob job : stale) {
            job.markFailed("timeout", now);
            log.warn("EnrichmentJob expired: jobId={} policyId={} attempt={}",
                    job.getId(), job.getPolicyId(), job.getAttempt());
        }
    }
}
