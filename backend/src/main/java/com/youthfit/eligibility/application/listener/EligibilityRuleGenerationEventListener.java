package com.youthfit.eligibility.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EligibilityRuleGenerationEventListener {

    private static final Logger log = LoggerFactory.getLogger(EligibilityRuleGenerationEventListener.class);
    private static final String CODE_BASED_VERSION = "code-v1";
    private static final String SKIP_REASON_CODE_BASED = "code-v1 deterministic rules already exist";

    private final EligibilityRuleGenerationService eligibilityRuleGenerationService;
    private final EligibilityRuleRepository ruleRepository;
    private final PolicyProcessingStepService stepService;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyUpserted(PolicyUpsertedEvent event) {
        Long stepId = stepService.markStarted(event.policyId(), ProcessingStep.RULE);
        if (hasCodeBasedRules(event.policyId())) {
            log.info("deterministic 룰 존재, LLM 추출 스킵: policyId={}", event.policyId());
            stepService.markFinished(stepId, ProcessingStatus.SKIPPED, SKIP_REASON_CODE_BASED, null);
            return;
        }
        try {
            eligibilityRuleGenerationService.generateRules(
                    new GenerateEligibilityRulesCommand(event.policyId()));
            stepService.markFinished(stepId, ProcessingStatus.SUCCESS, null, null);
        } catch (Exception e) {
            stepService.markFinished(stepId, ProcessingStatus.FAILED, summarize(e), null);
            log.warn("적합도 룰 추출 실패 (event=PolicyUpsertedEvent): policyId={}", event.policyId(), e);
        }
    }

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAttachmentReindexed(PolicyAttachmentReindexedEvent event) {
        Long stepId = stepService.markStarted(event.policyId(), ProcessingStep.RULE);
        if (hasCodeBasedRules(event.policyId())) {
            log.info("deterministic 룰 존재, LLM 재추출 스킵: policyId={}", event.policyId());
            stepService.markFinished(stepId, ProcessingStatus.SKIPPED, SKIP_REASON_CODE_BASED, null);
            return;
        }
        try {
            eligibilityRuleGenerationService.generateRules(
                    new GenerateEligibilityRulesCommand(event.policyId()));
            stepService.markFinished(stepId, ProcessingStatus.SUCCESS, null, null);
        } catch (Exception e) {
            stepService.markFinished(stepId, ProcessingStatus.FAILED, summarize(e), null);
            log.warn("적합도 룰 재추출 실패 (event=PolicyAttachmentReindexedEvent): policyId={}", event.policyId(), e);
        }
    }

    private boolean hasCodeBasedRules(Long policyId) {
        return ruleRepository.findAllByPolicyId(policyId).stream()
                .anyMatch(r -> CODE_BASED_VERSION.equals(r.getExtractionVersion()));
    }

    private static String summarize(Throwable t) {
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
