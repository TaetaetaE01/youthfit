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
        List<ApplyMethod> applyMethods
) {
    public record Attachment(String name, String url, String mediaType) {}
    public record ReferenceSite(String name, String url) {}
    public record ApplyMethod(String stageName, String description) {}
}
