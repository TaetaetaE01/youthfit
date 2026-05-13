package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.SourceType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record PolicyDetailResponse(
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
        LocalDateTime updatedAt,
        Enrichment enrichment
) {
    public record Attachment(Long id, String name, String url, String mediaType) {
        static Attachment from(PolicyDetailResult.Attachment a) {
            return new Attachment(a.id(), a.name(), a.url(), a.mediaType());
        }
    }

    public record ReferenceSite(String name, String url) {
        static ReferenceSite from(PolicyDetailResult.ReferenceSite s) {
            return new ReferenceSite(s.name(), s.url());
        }
    }

    public record ApplyMethod(String stageName, String description) {
        static ApplyMethod from(PolicyDetailResult.ApplyMethod m) {
            return new ApplyMethod(m.stageName(), m.description());
        }
    }

    public record SubRegion(String code, String sidoCode, String sidoName, String name) {
        static SubRegion from(PolicyDetailResult.SubRegion s) {
            return new SubRegion(s.code(), s.sidoCode(), s.sidoName(), s.name());
        }
    }

    public record Enrichment(
            String sourceUrl,
            Instant fetchedAt,
            Sections sections,
            List<ExtraAttachment> extraAttachments
    ) {
        static Enrichment from(PolicyDetailResult.Enrichment src) {
            if (src == null) return null;
            Sections sections = src.sections() == null ? null
                    : new Sections(
                            src.sections().supportTarget(),
                            src.sections().supportContent(),
                            src.sections().applyMethod(),
                            src.sections().requiredDocuments(),
                            src.sections().deadlineNote(),
                            src.sections().policyOverview(),
                            src.sections().eligibilityCriteria(),
                            src.sections().operatingOrganization(),
                            src.sections().contactPhone()
                    );
            return new Enrichment(
                    src.sourceUrl(),
                    src.fetchedAt(),
                    sections,
                    src.extraAttachments().stream()
                            .map(a -> new ExtraAttachment(a.name(), a.url()))
                            .toList()
            );
        }

        public record Sections(
                String supportTarget,
                String supportContent,
                String applyMethod,
                String requiredDocuments,
                String deadlineNote,
                String policyOverview,
                String eligibilityCriteria,
                String operatingOrganization,
                String contactPhone
        ) {}

        public record ExtraAttachment(String name, String url) {}
    }

    public static PolicyDetailResponse from(PolicyDetailResult result) {
        return new PolicyDetailResponse(
                result.id(),
                result.title(),
                result.summary(),
                result.body(),
                result.supportTarget(),
                result.selectionCriteria(),
                result.supportContent(),
                result.organization(),
                result.contact(),
                result.category(),
                result.regionCode(),
                result.subRegions().stream().map(SubRegion::from).toList(),
                result.applyStart(),
                result.applyEnd(),
                result.referenceYear(),
                result.supportCycle(),
                result.provideType(),
                result.screeningMethod(),
                result.submissionDocuments(),
                result.additionalQualification(),
                result.participationRestriction(),
                result.additionalNotes(),
                result.businessPeriodStart(),
                result.businessPeriodEnd(),
                result.businessPeriodNote(),
                result.supportScale(),
                result.firstComeFirstServed(),
                result.applyUrl(),
                result.status(),
                result.detailLevel(),
                result.lifeTags(),
                result.themeTags(),
                result.targetTags(),
                result.attachments().stream().map(Attachment::from).toList(),
                result.referenceSites().stream().map(ReferenceSite::from).toList(),
                result.applyMethods().stream().map(ApplyMethod::from).toList(),
                result.sourceType(),
                result.sourceLabel(),
                result.sourceUrl(),
                result.createdAt(),
                result.updatedAt(),
                Enrichment.from(result.enrichment())
        );
    }
}
