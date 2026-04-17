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
        String organization,
        String contact,
        List<String> lifeTags,
        List<String> themeTags,
        List<String> targetTags,
        List<Attachment> attachments
) {
    public record Attachment(String name, String url, String mediaType) {}
}
