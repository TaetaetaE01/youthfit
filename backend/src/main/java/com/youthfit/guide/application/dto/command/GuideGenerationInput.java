package com.youthfit.guide.application.dto.command;

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
        List<String> chunkContents
) {

    public GuideGenerationInput {
        if (policyId == null) {
            throw new IllegalArgumentException("policyId는 null일 수 없습니다");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title은 비어있을 수 없습니다");
        }
        chunkContents = chunkContents == null ? List.of() : List.copyOf(chunkContents);
    }

    public static GuideGenerationInput of(Policy policy, List<PolicyDocument> chunks) {
        List<String> chunkTexts = chunks == null
                ? List.of()
                : chunks.stream().map(PolicyDocument::getContent).collect(Collectors.toList());

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
                chunkTexts
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
        for (int i = 0; i < chunkContents.size(); i++) {
            sb.append("[chunk-").append(i).append("]\n").append(chunkContents.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("[").append(key).append("]\n").append(value).append("\n\n");
        }
    }
}
