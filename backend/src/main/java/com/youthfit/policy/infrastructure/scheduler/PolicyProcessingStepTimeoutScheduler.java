package com.youthfit.policy.infrastructure.scheduler;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * IN_PROGRESS 로 stale 한 {@code policy_processing_step} 행을 FAILED 로 강제 마감하는 스케줄러.
 *
 * <p>운영 환경 마이그레이션 + 상시 안전장치 두 가지 역할.
 * {@link com.youthfit.admin.application.listener.PolicyReprocessRequestedEventListener} 가
 * NPE / OOM / kill -9 등으로 {@code markFinished} 호출에 실패해도, 본 스케줄러가 1분 주기로
 * threshold (10분) 초과 행을 자동으로 FAILED 로 정리한다.</p>
 *
 * <p>{@link com.youthfit.policy.infrastructure.scheduler.EnrichmentJobTimeoutScheduler} 와 동일 패턴.</p>
 */
@Component
public class PolicyProcessingStepTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolicyProcessingStepTimeoutScheduler.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final PolicyProcessingStepRepository repository;
    private final Clock clock;

    public PolicyProcessingStepTimeoutScheduler(PolicyProcessingStepRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${policy.processing-step.timeout.fixed-delay-ms:60000}")
    @Transactional
    public void expireStaleSteps() {
        Instant threshold = Instant.now(clock).minus(TIMEOUT);
        List<PolicyProcessingStep> stale = repository.findActiveStaleBefore(threshold);
        if (stale.isEmpty()) return;

        for (PolicyProcessingStep step : stale) {
            step.markTimedOut();
            log.warn("PolicyProcessingStep expired: id={} policyId={} step={} attempt={}",
                    step.getId(), step.getPolicyId(), step.getStep(), step.getAttempt());
        }
    }
}
