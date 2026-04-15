package com.youthfit.ingestion.application.dto.command;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record IngestPolicyCommand(
        String sourceUrl,
        String sourceType,
        LocalDateTime fetchedAt,
        String title,
        String body,
        String category,
        String region,
        LocalDate applyStart,
        LocalDate applyEnd
) {
}
