package com.youthfit.guide.application.dto.command;

import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.rag.domain.model.PolicyDocument;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public record GuideGenerationInput(
        Long policyId,
        String title,
        Integer referenceYear,
        String summary,
        String body,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String contact,
        String organization,
        EnrichmentInput enrichment,
        List<ChunkInput> chunks,
        IncomeBracketReference referenceData
) {

    public GuideGenerationInput {
        if (policyId == null) {
            throw new IllegalArgumentException("policyId는 null일 수 없습니다");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title은 비어있을 수 없습니다");
        }
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        // referenceData는 nullable 허용 (yaml 누락 시 호출부에서 fallback 처리)
    }

    public record EnrichmentInput(
            String supportTarget,
            String supportContent,
            String applyMethod,
            String requiredDocuments,
            String deadlineNote,
            String policyOverview,
            String eligibilityCriteria,
            String operatingOrganization,
            String contactPhone,
            Instant fetchedAt,
            String sourceUrl,
            Double confidence
    ) {}

    public static GuideGenerationInput of(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference referenceData) {
        EnrichmentInput enrichmentInput = null;
        if (policy.getEnrichment() != null && policy.getEnrichment().isExposable()) {
            PolicyEnrichment e = policy.getEnrichment();
            PolicyEnrichment.Sections s = e.sections();
            enrichmentInput = new EnrichmentInput(
                    s.supportTarget(),
                    s.supportContent(),
                    s.applyMethod(),
                    s.requiredDocuments(),
                    s.deadlineNote(),
                    s.policyOverview(),
                    s.eligibilityCriteria(),
                    s.operatingOrganization(),
                    s.contactPhone(),
                    e.fetchedAt(),
                    e.sourceUrl(),
                    e.confidence()
            );
        }

        List<ChunkInput> chunkInputs = chunks == null
                ? List.of()
                : chunks.stream()
                        .map(d -> new ChunkInput(
                                d.getContent(),
                                d.getAttachmentId(),
                                d.getPageStart(),
                                d.getPageEnd()))
                        .collect(Collectors.toList());

        return new GuideGenerationInput(
                policy.getId(),
                policy.getTitle(),
                policy.getReferenceYear(),
                policy.getSummary(),
                policy.getBody(),
                policy.getSupportTarget(),
                policy.getSelectionCriteria(),
                policy.getSupportContent(),
                policy.getContact(),
                policy.getOrganization(),
                enrichmentInput,
                chunkInputs,
                referenceData
        );
    }

    public String combinedSourceText() {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "summary", summary);
        appendSection(sb, "body", body);
        appendSection(sb, "supportTarget", supportTarget);
        appendSection(sb, "selectionCriteria", selectionCriteria);
        appendSection(sb, "supportContent", supportContent);
        appendSection(sb, "contact", contact);
        appendSection(sb, "organization", organization);

        if (enrichment != null) {
            sb.append("[enrichment.meta]\n");
            if (enrichment.sourceUrl() != null) sb.append("sourceUrl=").append(enrichment.sourceUrl()).append("\n");
            if (enrichment.fetchedAt() != null) sb.append("fetchedAt=").append(enrichment.fetchedAt()).append("\n");
            if (enrichment.confidence() != null) sb.append("confidence=").append(enrichment.confidence()).append("\n");
            sb.append("(LLM 은 이 메타 절을 출력 생성에 직접 인용하지 않는다. 출처 라벨 참고 정보로만 활용)\n\n");

            appendSection(sb, "enrichment.policyOverview", enrichment.policyOverview());
            appendSection(sb, "enrichment.supportTarget", enrichment.supportTarget());
            appendSection(sb, "enrichment.eligibilityCriteria", enrichment.eligibilityCriteria());
            appendSection(sb, "enrichment.supportContent", enrichment.supportContent());
            appendSection(sb, "enrichment.applyMethod", enrichment.applyMethod());
            appendSection(sb, "enrichment.requiredDocuments", enrichment.requiredDocuments());
            appendSection(sb, "enrichment.deadlineNote", enrichment.deadlineNote());
            appendSection(sb, "enrichment.operatingOrganization", enrichment.operatingOrganization());
            appendSection(sb, "enrichment.contactPhone", enrichment.contactPhone());
        }

        if (referenceYear != null) {
            sb.append("[referenceYear]\n").append(referenceYear).append("\n\n");
        }
        for (int i = 0; i < chunks.size(); i++) {
            ChunkInput c = chunks.get(i);
            sb.append('[').append("chunk-").append(i);
            if (c.attachmentId() == null) {
                sb.append(" source=BODY]\n");
            } else {
                sb.append(" source=ATTACHMENT attachment-id=").append(c.attachmentId());
                if (c.pageStart() != null) {
                    sb.append(" pages=").append(c.pageStart()).append('-').append(c.pageEnd());
                }
                sb.append("]\n");
            }
            sb.append(c.content()).append("\n\n");
        }
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("[").append(key).append("]\n").append(value).append("\n\n");
        }
    }
}
