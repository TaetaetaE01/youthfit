package com.youthfit.ingestion.presentation.dto.request;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record IngestPolicyRequest(
        @NotNull @Valid SourceInfo source,
        @NotNull @Valid RawData rawData
) {

    public IngestPolicyCommand toCommand() {
        return new IngestPolicyCommand(
                source.url(),
                source.type(),
                source.fetchedAt(),
                rawData.externalId(),
                rawData.title(),
                rawData.summary(),
                rawData.body(),
                rawData.category(),
                rawData.region(),
                rawData.applyStart(),
                rawData.applyEnd(),
                rawData.organization(),
                rawData.contact(),
                rawData.lifeTags(),
                rawData.themeTags(),
                rawData.targetTags(),
                rawData.attachments() == null ? List.of() : rawData.attachments().stream()
                        .map(a -> new IngestPolicyCommand.Attachment(a.name(), a.url(), a.mediaType()))
                        .toList()
        );
    }

    public record SourceInfo(
            @NotBlank String url,
            @NotBlank String type,
            @NotNull LocalDateTime fetchedAt
    ) {}

    public record RawData(
            String externalId,
            @NotBlank String title,
            String summary,
            @NotBlank String body,
            @NotBlank String category,
            @NotBlank String region,
            LocalDate applyStart,
            LocalDate applyEnd,
            String organization,
            String contact,
            List<String> lifeTags,
            List<String> themeTags,
            List<String> targetTags,
            List<@Valid Attachment> attachments
    ) {}

    public record Attachment(
            @NotBlank String name,
            @NotBlank String url,
            String mediaType
    ) {}
}
