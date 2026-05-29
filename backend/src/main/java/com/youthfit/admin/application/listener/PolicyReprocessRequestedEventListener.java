package com.youthfit.admin.application.listener;

import com.youthfit.common.event.PolicyReprocessRequestedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.service.RagIndexingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 어드민 전체 재처리 이벤트 수신 listener.
 *
 * <p>{@link PolicyReprocessRequestedEvent} 를 받아 ENRICHMENT/GUIDE/RULE/RAG_INDEXING
 * 4 단계를 순차 실행하고 step 행을 마감한다. ENRICHMENT 는 MVP 에서 n8n 재크롤이 필요해
 * SKIPPED 로 처리 (admin {@code retryStep(ENRICHMENT)} 와 동일한 정책).</p>
 *
 * <p>호출 패턴은 {@code AdminPolicyProcessingService.retryStep} 의 단계별 호출 그대로 재사용.
 * stepIds 인덱스는 발행 측 ({@code AdminPolicyProcessingService.reprocess}) 가 코드 레벨에서
 * ENRICHMENT(0), GUIDE(1), RULE(2), RAG_INDEXING(3) 순서로 보장한다.</p>
 *
 * <p>비동기 실행은 기존 {@code llmExecutor} 빈 (core 2 / max 4 / queue 100 / CallerRunsPolicy)
 * 재사용. HTTP 응답이 LLM 호출 대기에 블록되지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class PolicyReprocessRequestedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PolicyReprocessRequestedEventListener.class);
    private static final String ENRICHMENT_SKIP_REASON = "MVP: ENRICHMENT manual trigger 미연결";
    private static final String POLICY_NOT_FOUND_REASON = "정책 없음";

    private final PolicyRepository policyRepository;
    private final PolicyProcessingStepService stepService;
    private final RagIndexingService ragIndexingService;
    private final GuideGenerationService guideGenerationService;
    private final EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @Async("llmExecutor")
    @EventListener
    public void onPolicyReprocessRequested(PolicyReprocessRequestedEvent event) {
        List<Long> ids = event.stepIds();
        Policy policy = policyRepository.findById(event.policyId()).orElse(null);
        if (policy == null) {
            log.warn("재처리 이벤트 도착 — 정책 없음: policyId={}", event.policyId());
            ids.forEach(id -> stepService.markFinished(id, ProcessingStatus.FAILED, POLICY_NOT_FOUND_REASON, null));
            return;
        }

        // 인덱스 매칭: [0]=ENRICHMENT, [1]=GUIDE, [2]=RULE, [3]=RAG_INDEXING
        stepService.markFinished(ids.get(0), ProcessingStatus.SKIPPED, ENRICHMENT_SKIP_REASON, null);

        runWithStep(ids.get(1), () -> guideGenerationService.generateGuide(
                new GenerateGuideCommand(policy.getId(), policy.getTitle(), null)));
        runWithStep(ids.get(2), () -> eligibilityRuleGenerationService.generateRules(
                new GenerateEligibilityRulesCommand(policy.getId())));
        runWithStep(ids.get(3), () -> ragIndexingService.indexPolicyDocument(
                new IndexPolicyDocumentCommand(policy.getId(), policy.getBody(), policy.getEnrichment())));
    }

    private void runWithStep(Long stepRowId, Runnable action) {
        try {
            action.run();
            stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS, null, null);
        } catch (Exception e) {
            log.warn("재처리 단계 실패: stepRowId={} message={}", stepRowId, e.getMessage());
            stepService.markFinished(stepRowId, ProcessingStatus.FAILED, e.getMessage(), null);
        }
    }
}
