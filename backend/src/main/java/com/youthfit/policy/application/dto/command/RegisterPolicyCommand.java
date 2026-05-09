package com.youthfit.policy.application.dto.command;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.SourceType;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record RegisterPolicyCommand(
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
        // ── 신규 정책 상세 11개 ──
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
        Set<String> lifeTags,
        Set<String> themeTags,
        Set<String> targetTags,
        List<Attachment> attachments,
        List<ReferenceSite> referenceSites,
        List<ApplyMethod> applyMethods,
        SourceType sourceType,
        String externalId,
        String sourceUrl,
        String rawJson,
        String sourceHash
) {
    public record Attachment(String name, String url, String mediaType) {}
    public record ReferenceSite(String name, String url) {}
    public record ApplyMethod(String stageName, String description) {}
}
