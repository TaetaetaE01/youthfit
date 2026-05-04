package com.youthfit.eligibility.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
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

    private final EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyUpserted(PolicyUpsertedEvent event) {
        try {
            eligibilityRuleGenerationService.generateRules(
                    new GenerateEligibilityRulesCommand(event.policyId()));
        } catch (Exception e) {
            log.warn("적합도 룰 추출 실패 (event=PolicyUpsertedEvent): policyId={}", event.policyId(), e);
        }
    }

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAttachmentReindexed(PolicyAttachmentReindexedEvent event) {
        try {
            eligibilityRuleGenerationService.generateRules(
                    new GenerateEligibilityRulesCommand(event.policyId()));
        } catch (Exception e) {
            log.warn("적합도 룰 재추출 실패 (event=PolicyAttachmentReindexedEvent): policyId={}", event.policyId(), e);
        }
    }
}
