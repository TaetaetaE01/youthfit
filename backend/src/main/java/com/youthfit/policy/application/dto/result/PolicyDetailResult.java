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
        List<SubRegion> subRegions,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        String supportCycle,
        String provideType,
        // ── 신규 ──
        String screeningMethod,
        String submissionDocuments,
        String additionalQualification,
        String participationRestriction,
        String additionalNotes,
        LocalDate businessPeriodStart,
        LocalDate businessPeriodEnd,
        String businessPeriodNote,
        Integer supportScale,
        boolean firstComeFirstServed,
        String applyUrl,
        // ── 기존 ──
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
    public record Attachment(Long id, String name, String url, String mediaType) {
        public static Attachment from(PolicyAttachment attachment) {
            return new Attachment(
                    attachment.getId(),
                    attachment.getName(),
                    attachment.getUrl(),
                    attachment.getMediaType());
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

    public record SubRegion(String code, String sidoCode, String sidoName, String name) {
        public static SubRegion from(RegionCode rc) {
            return new SubRegion(rc.code(), rc.sidoCode(), rc.sidoName(), rc.name());
        }
    }

    public static PolicyDetailResult from(Policy policy, PolicySource source, List<SubRegion> subRegions) {
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
                subRegions == null ? List.of() : subRegions,
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getReferenceYear(),
                policy.getSupportCycle(),
                policy.getProvideType(),
                policy.getScreeningMethod(),
                policy.getSubmissionDocuments(),
                policy.getAdditionalQualification(),
                policy.getParticipationRestriction(),
                policy.getAdditionalNotes(),
                policy.getBusinessPeriodStart(),
                policy.getBusinessPeriodEnd(),
                policy.getBusinessPeriodNote(),
                policy.getSupportScale(),
                policy.isFirstComeFirstServed(),
                policy.getApplyUrl(),
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
