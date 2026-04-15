package com.youthfit.ingestion.presentation.dto.request;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record IngestPolicyRequest(
        @NotNull @Valid SourceInfo source,
        @NotNull @Valid RawData rawData
) {

    public IngestPolicyCommand toCommand() {
        return new IngestPolicyCommand(
                source.url(),
                source.type(),
                source.fetchedAt(),
                rawData.title(),
                rawData.body(),
                rawData.category(),
                rawData.region(),
                rawData.applyStart(),
                rawData.applyEnd()
        );
    }

    public record SourceInfo(
            @NotBlank String url,
            @NotBlank String type,
            @NotNull LocalDateTime fetchedAt
    ) {}

    public record RawData(
            @NotBlank String title,
            @NotBlank String body,
            @NotBlank String category,
            @NotBlank String region,
            LocalDate applyStart,
            LocalDate applyEnd
    ) {}
}
