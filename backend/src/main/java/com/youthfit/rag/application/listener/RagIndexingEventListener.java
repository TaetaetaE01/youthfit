package com.youthfit.rag.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.service.RagIndexingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RagIndexingEventListener {

    private static final Logger log = LoggerFactory.getLogger(RagIndexingEventListener.class);

    private final PolicyRepository policyRepository;
    private final RagIndexingService ragIndexingService;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyUpserted(PolicyUpsertedEvent event) {
        try {
            Optional<Policy> policyOpt = policyRepository.findById(event.policyId());
            if (policyOpt.isEmpty()) {
                log.warn("정책 미존재 — RAG 1차 인덱싱 스킵: policyId={}", event.policyId());
                return;
            }
            Policy policy = policyOpt.get();
            String body = policy.getBody();
            if (body == null || body.isBlank()) {
                log.info("정책 본문 비어 있음 — RAG 1차 인덱싱 스킵: policyId={}", event.policyId());
                return;
            }
            ragIndexingService.indexPolicyDocument(
                    new IndexPolicyDocumentCommand(event.policyId(), body, policy.getEnrichment()));
        } catch (Exception e) {
            log.warn("RAG 1차 인덱싱 실패 (event=PolicyUpsertedEvent): policyId={}",
                    event.policyId(), e);
        }
    }
}
