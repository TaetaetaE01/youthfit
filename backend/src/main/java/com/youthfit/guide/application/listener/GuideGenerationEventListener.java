package com.youthfit.guide.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
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
public class GuideGenerationEventListener {

    private static final Logger log = LoggerFactory.getLogger(GuideGenerationEventListener.class);

    private final GuideGenerationService guideGenerationService;
    private final PolicyProcessingStepService stepService;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyUpserted(PolicyUpsertedEvent event) {
        Long stepId = stepService.markStarted(event.policyId(), ProcessingStep.GUIDE);
        try {
            guideGenerationService.generateGuide(
                    new GenerateGuideCommand(event.policyId(), event.title(), null));
            stepService.markFinished(stepId, ProcessingStatus.SUCCESS, null, null);
        } catch (Exception e) {
            stepService.markFinished(stepId, ProcessingStatus.FAILED, summarize(e), null);
            log.warn("가이드 생성 실패 (event=PolicyUpsertedEvent): policyId={}", event.policyId(), e);
        }
    }

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAttachmentReindexed(PolicyAttachmentReindexedEvent event) {
        Long stepId = stepService.markStarted(event.policyId(), ProcessingStep.GUIDE);
        try {
            guideGenerationService.generateGuide(
                    new GenerateGuideCommand(event.policyId(), null, null));
            stepService.markFinished(stepId, ProcessingStatus.SUCCESS, null, null);
        } catch (Exception e) {
            stepService.markFinished(stepId, ProcessingStatus.FAILED, summarize(e), null);
            log.warn("가이드 재생성 실패 (event=PolicyAttachmentReindexedEvent): policyId={}", event.policyId(), e);
        }
    }

    private static String summarize(Throwable t) {
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
