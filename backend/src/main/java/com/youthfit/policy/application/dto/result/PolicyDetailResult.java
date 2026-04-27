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
        Integer referenceYear,
        String supportCycle,
        String provideType,
        PolicyStatus status,
        DetailLevel detailLevel,
        Set<String> lifeTags,
        Set<String> themeTags,
        Set<String> targetTags,
        List<Attachment> attachments,
        List<ReferenceSite> referenceSites,
        List<ApplyMethod> applyMethods,
        SourceType sourceType,
        String sourceLabel,
        String sourceUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Attachment(String name, String url, String mediaType) {
        public static Attachment from(PolicyAttachment attachment) {
            return new Attachment(attachment.getName(), attachment.getUrl(), attachment.getMediaType());
        }
    }

    public record ReferenceSite(String name, String url) {
        public static ReferenceSite from(PolicyReferenceSite site) {
            return new ReferenceSite(site.name(), site.url());
        }
    }

    public record ApplyMethod(String stageName, String description) {
        public static ApplyMethod from(PolicyApplyMethod method) {
            return new ApplyMethod(method.stageName(), method.description());
        }
    }

    public static PolicyDetailResult from(Policy policy, PolicySource source) {
        SourceType sourceType = source != null ? source.getSourceType() : null;
        String sourceLabel = sourceType != null ? sourceType.getLabel() : null;
        String sourceUrl = source != null ? source.getSourceUrl() : null;
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
                policy.getReferenceYear(),
                policy.getSupportCycle(),
                policy.getProvideType(),
                policy.getStatus(),
                policy.getDetailLevel(),
                Set.copyOf(policy.getLifeTags()),
                Set.copyOf(policy.getThemeTags()),
                Set.copyOf(policy.getTargetTags()),
                policy.getAttachments().stream().map(Attachment::from).toList(),
                policy.getReferenceSites().stream().map(ReferenceSite::from).toList(),
                policy.getApplyMethods().stream().map(ApplyMethod::from).toList(),
                sourceType,
                sourceLabel,
                sourceUrl,
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
