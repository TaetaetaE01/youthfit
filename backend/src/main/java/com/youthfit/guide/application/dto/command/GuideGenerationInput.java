package com.youthfit.guide.application.dto.command;

import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.rag.domain.model.PolicyDocument;

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

    public static GuideGenerationInput of(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference referenceData) {
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
