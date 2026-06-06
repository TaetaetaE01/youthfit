package com.youthfit.ingestion.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult;
import com.youthfit.ingestion.application.port.AttachmentEmbeddingJudge;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AttachmentReindexService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentReindexService.class);

    private static final Pattern PAGE_SENTINEL =
            Pattern.compile("\\f<page=([^>]+)>\\n");

    private static final int MIN_ATTACHMENTS_FOR_GATE = 2;
    private static final int PREVIEW_CHARS = 1500;

    private final PolicyRepository policyRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final RagIndexingService ragIndexingService;
    private final ApplicationEventPublisher eventPublisher;
    private final CostGuard costGuard;
    private final AttachmentEmbeddingJudge embeddingJudge;

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
        List<PolicyAttachment> selected = selectForEmbedding(policy, attachments);
        String merged = mergeContent(policy, selected);

        IndexPolicyDocumentCommand cmd = new IndexPolicyDocumentCommand(resolvedId, merged, policy.getEnrichment());
        IndexingResult result = ragIndexingService.indexPolicyDocument(cmd);
        log.info("reindex policyId={} chunks={} updated={}", resolvedId, result.chunkCount(), result.updated());

        if (result.updated()) {
            eventPublisher.publishEvent(new PolicyAttachmentReindexedEvent(resolvedId));
            log.info("attachment reindex event published: policyId={}", resolvedId);
        }
    }

    /**
     * 첨부 ≥2개일 때 LLM 게이트로 임베딩 가치를 판정하고,
     * 포함 판정(또는 미판정·1개 이하)인 첨부만 반환한다.
     */
    List<PolicyAttachment> selectForEmbedding(Policy policy, List<PolicyAttachment> attachments) {
        if (attachments.size() < MIN_ATTACHMENTS_FOR_GATE) {
            return attachments;
        }
        List<PolicyAttachment> undecided = attachments.stream()
                .filter(a -> a.getEmbeddingIncluded() == null)
                .toList();
        if (!undecided.isEmpty()) {
            judgeAndPersist(policy, undecided);
        }
        // embeddingIncluded == false 인 것만 제외. null(판정 안 됨)·true 는 포함.
        return attachments.stream()
                .filter(a -> !Boolean.FALSE.equals(a.getEmbeddingIncluded()))
                .toList();
    }

    private void judgeAndPersist(Policy policy, List<PolicyAttachment> undecided) {
        try {
            AttachmentEmbeddingResult result = embeddingJudge.judge(toCommand(policy, undecided));
            for (PolicyAttachment a : undecided) {
                AttachmentEmbeddingResult.AttachmentDecision d =
                        result.findByAttachmentId(a.getId()).orElse(null);
                if (d == null) {
                    a.decideEmbedding(true, "gate-no-decision"); // 누락 → 보수적 포함
                } else {
                    a.decideEmbedding(d.embed(), d.reason());
                }
                attachmentRepository.save(a);
            }
        } catch (Exception e) {
            log.warn("attachment embedding gate 실패, fail-open 으로 전체 포함: policyId={} err={}",
                    policy.getId(), e.toString());
            for (PolicyAttachment a : undecided) {
                a.decideEmbedding(true, "gate-failed: " + e.getClass().getSimpleName());
                attachmentRepository.save(a);
            }
        }
    }

    private AttachmentEmbeddingJudgeCommand toCommand(Policy policy, List<PolicyAttachment> undecided) {
        List<AttachmentEmbeddingJudgeCommand.AttachmentItem> items = new ArrayList<>();
        for (PolicyAttachment a : undecided) {
            String text = a.getExtractedText() == null ? "" : a.getExtractedText();
            String preview = text.length() > PREVIEW_CHARS ? text.substring(0, PREVIEW_CHARS) : text;
            items.add(new AttachmentEmbeddingJudgeCommand.AttachmentItem(a.getId(), a.getName(), preview));
        }
        String summary = policy.getBody() == null ? "" :
                (policy.getBody().length() > 300 ? policy.getBody().substring(0, 300) : policy.getBody());
        return new AttachmentEmbeddingJudgeCommand(policy.getTitle(), summary, items);
    }

    String mergeContent(Policy policy, List<PolicyAttachment> attachments) {
        long maxBytes = (long) maxContentKb * 1024L;
        StringBuilder sb = new StringBuilder();
        sb.append("=== 정책 본문 ===\n");
        sb.append(policy.getBody() == null ? "" : policy.getBody());

        // Pre-fetch id, name and text for all attachments eagerly before deciding inclusion
        record AttachmentEntry(Long id, String name, String text) {}
        List<AttachmentEntry> entries = attachments.stream()
                .map(a -> new AttachmentEntry(
                        a.getId(),
                        a.getName(),
                        a.getExtractedText() == null ? "" : sentinelToMarkers(a.getExtractedText())))
                .toList();

        long remaining = maxBytes - sb.length();
        boolean capReached = false;
        for (AttachmentEntry entry : entries) {
            if (capReached) {
                log.debug("attachment skipped from reindex (cap reached): {}", entry.name());
                continue;
            }
            String header = "\n\n=== 첨부 attachment-id=" + entry.id()
                    + " name=\"" + entry.name() + "\" ===\n";
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

    private String sentinelToMarkers(String extractedText) {
        return PAGE_SENTINEL.matcher(extractedText)
                .replaceAll(mr -> "\n--- page=" + java.util.regex.Matcher.quoteReplacement(mr.group(1)) + " ---\n");
    }
}
