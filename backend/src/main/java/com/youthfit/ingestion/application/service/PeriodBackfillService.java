package com.youthfit.ingestion.application.service;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyPeriodUpdated;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.ResolvedPeriod;
import com.youthfit.ingestion.domain.service.PeriodResolver;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PeriodBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PeriodBackfillService.class);
    private static final double BACKFILL_THRESHOLD = 0.70;
    private static final double OVERWRITE_MARGIN = 0.05;

    private final PolicyRepository policyRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final PeriodResolver periodResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Async("periodBackfillExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttachmentsReindexed(PolicyAttachmentReindexedEvent event) {
        Optional<Policy> policyOpt = policyRepository.findById(event.policyId());
        if (policyOpt.isEmpty()) return;
        Policy policy = policyOpt.get();

        Double current = policy.getApplyPeriodConfidence();
        if (current != null && current >= BACKFILL_THRESHOLD) {
            log.debug("period-backfill skipped: policyId={} confidence={}", event.policyId(), current);
            return;
        }

        List<String> attachmentTexts = attachmentRepository.findExtractedByPolicyId(event.policyId()).stream()
                .map(PolicyAttachment::getExtractedText)
                .filter(t -> t != null && !t.isBlank())
                .toList();

        ResolvedPeriod result = periodResolver.resolve(
                PeriodExtractionContext.forBackfill(policy, attachmentTexts));

        if (!shouldOverwrite(current, result)) {
            log.info("period-backfill no improvement: policyId={} current={} new={}",
                    event.policyId(), current, result.confidence());
            return;
        }

        policy.updateApplyPeriod(result.start(), result.end(),
                result.source(), result.confidence(), result.evidence());
        log.info("period-backfill updated: policyId={} source={} confidence={}",
                event.policyId(), result.source(), result.confidence());
        eventPublisher.publishEvent(new PolicyPeriodUpdated(policy.getId()));
    }

    private boolean shouldOverwrite(Double current, ResolvedPeriod r) {
        if (r.isEmpty()) return false;
        if (current == null) return true;
        return r.confidence() > current + OVERWRITE_MARGIN;
    }
}
