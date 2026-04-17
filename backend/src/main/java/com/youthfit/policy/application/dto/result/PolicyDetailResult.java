package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record PolicyDetailResult(
        Long id,
        String title,
        String summary,
        String body,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String organization,
        String contact,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        PolicyStatus status,
        DetailLevel detailLevel,
        Set<String> lifeTags,
        Set<String> themeTags,
        Set<String> targetTags,
        List<Attachment> attachments,
        String sourceUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Attachment(String name, String url, String mediaType) {
        public static Attachment from(PolicyAttachment attachment) {
            return new Attachment(attachment.getName(), attachment.getUrl(), attachment.getMediaType());
        }
    }

    public static PolicyDetailResult from(Policy policy, String sourceUrl) {
        return new PolicyDetailResult(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getBody(),
                policy.getSupportTarget(),
                policy.getSelectionCriteria(),
                policy.getSupportContent(),
                policy.getOrganization(),
                policy.getContact(),
                policy.getCategory(),
                policy.getRegionCode(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getStatus(),
                policy.getDetailLevel(),
                Set.copyOf(policy.getLifeTags()),
                Set.copyOf(policy.getThemeTags()),
                Set.copyOf(policy.getTargetTags()),
                policy.getAttachments().stream().map(Attachment::from).toList(),
                sourceUrl,
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
