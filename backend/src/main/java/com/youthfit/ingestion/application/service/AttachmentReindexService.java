package com.youthfit.ingestion.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttachmentReindexService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentReindexService.class);

    private final PolicyRepository policyRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final RagIndexingService ragIndexingService;
    private final GuideGenerationService guideGenerationService;
    private final CostGuard costGuard;

    @Setter
    @Value("${attachment.reindex.max-content-kb:200}")
    private int maxContentKb;

    public void reindex(Long policyId) {
        if (!costGuard.allows(policyId)) {
            costGuard.logSkip("attachment-reindex", policyId);
            return;
        }
        Optional<Policy> policyOpt = policyRepository.findById(policyId);
        if (policyOpt.isEmpty()) {
            log.warn("policy not found for reindex: {}", policyId);
            return;
        }
        Policy policy = policyOpt.get();
        Long resolvedId = policy.getId();

        List<PolicyAttachment> attachments = attachmentRepository.findExtractedByPolicyId(resolvedId);
        String merged = mergeContent(policy, attachments);

        IndexPolicyDocumentCommand cmd = new IndexPolicyDocumentCommand(resolvedId, merged);
        IndexingResult result = ragIndexingService.indexPolicyDocument(cmd);
        log.info("reindex policyId={} chunks={} updated={}", resolvedId, result.chunkCount(), result.updated());

        if (result.updated()) {
            guideGenerationService.generateGuide(new GenerateGuideCommand(resolvedId, policy.getTitle(), null));
            log.info("guide regenerated: policyId={}", resolvedId);
        }
    }

    String mergeContent(Policy policy, List<PolicyAttachment> attachments) {
        long maxBytes = (long) maxContentKb * 1024L;
        StringBuilder sb = new StringBuilder();
        sb.append("=== 정책 본문 ===\n");
        sb.append(policy.getBody() == null ? "" : policy.getBody());

        // Pre-fetch name and text for all attachments eagerly before deciding inclusion
        record AttachmentEntry(String name, String text) {}
        List<AttachmentEntry> entries = attachments.stream()
                .map(a -> new AttachmentEntry(a.getName(), a.getExtractedText() == null ? "" : a.getExtractedText()))
                .toList();

        long remaining = maxBytes - sb.length();
        boolean capReached = false;
        for (AttachmentEntry entry : entries) {
            if (capReached) {
                log.debug("attachment skipped from reindex (cap reached): {}", entry.name());
                continue;
            }
            String header = "\n\n=== 첨부: " + entry.name() + " ===\n";
            String body = entry.text();
            long needed = header.length() + body.length();
            if (needed <= remaining) {
                sb.append(header).append(body);
                remaining -= needed;
            } else if (remaining > header.length() + 50) {
                int allowedBody = (int) (remaining - header.length());
                sb.append(header).append(body, 0, allowedBody);
                log.info("attachment truncated: {} truncated_to={}/{}", entry.name(), allowedBody, body.length());
                remaining = 0;
                capReached = true;
            } else {
                log.info("attachment skipped from reindex (no room): {}", entry.name());
                capReached = true;
            }
        }
        return sb.toString();
    }
}
