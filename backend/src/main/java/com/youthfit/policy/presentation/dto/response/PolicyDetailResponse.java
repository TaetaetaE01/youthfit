package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.SourceType;

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
        static Attachment from(PolicyDetailResult.Attachment a) {
            return new Attachment(a.name(), a.url(), a.mediaType());
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
                result.applyStart(),
                result.applyEnd(),
                result.referenceYear(),
                result.supportCycle(),
                result.provideType(),
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
                result.updatedAt()
        );
    }
}
