package com.youthfit.ingestion.application.dto.command;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record IngestPolicyCommand(
        String sourceUrl,
        String sourceType,
        LocalDateTime fetchedAt,
        String externalId,
        String title,
        String summary,
        String body,
        String category,
        String region,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        String supportCycle,
        String provideType,
        String organization,
        String contact,
        List<String> lifeTags,
        List<String> themeTags,
        List<String> targetTags,
        List<Attachment> attachments,
        List<ReferenceSite> referenceSites,
        List<ApplyMethod> applyMethods,
        // ── 신규 텍스트/구조화 11개 ──
        String screeningMethod,
        String submissionDocuments,
        String additionalQualification,
        String participationRestriction,
        String additionalNotes,
        LocalDate businessPeriodStart,
        LocalDate businessPeriodEnd,
        String businessPeriodNote,
        Integer supportScale,
        Boolean firstComeFirstServed,
        String applyUrl,
        // ── 신규 rawCodes ──
        RawCodes rawCodes
) {
    public record Attachment(String name, String url, String mediaType) {}
    public record ReferenceSite(String name, String url) {}
    public record ApplyMethod(String stageName, String description) {}

    public record RawCodes(
            Integer ageMin,
            Integer ageMax,
            String ageLimitYn,
            String maritalStatusCd,
            String earnConditionCd,
            Integer earnMin,
            Integer earnMax,
            String earnEtcCn,
            String employmentKindCd,
            String educationCd,
            String majorFieldCd,
            String specializationCd,
            List<String> zipCodes
    ) {}
}
