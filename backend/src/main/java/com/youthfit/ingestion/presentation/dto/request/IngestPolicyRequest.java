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
                rawData.referenceYear(),
                rawData.supportCycle(),
                rawData.provideType(),
                rawData.organization(),
                rawData.contact(),
                rawData.lifeTags(),
                rawData.themeTags(),
                rawData.targetTags(),
                rawData.attachments() == null ? List.of() : rawData.attachments().stream()
                        .map(a -> new IngestPolicyCommand.Attachment(a.name(), a.url(), a.mediaType()))
                        .toList(),
                rawData.referenceSites() == null ? List.of() : rawData.referenceSites().stream()
                        .map(s -> new IngestPolicyCommand.ReferenceSite(s.name(), s.url()))
                        .toList(),
                rawData.applyMethods() == null ? List.of() : rawData.applyMethods().stream()
                        .map(m -> new IngestPolicyCommand.ApplyMethod(m.stageName(), m.description()))
                        .toList(),
                rawData.screeningMethod(),
                rawData.submissionDocuments(),
                rawData.additionalQualification(),
                rawData.participationRestriction(),
                rawData.additionalNotes(),
                rawData.businessPeriodStart(),
                rawData.businessPeriodEnd(),
                rawData.businessPeriodNote(),
                rawData.supportScale(),
                rawData.firstComeFirstServed(),
                rawData.applyUrl(),
                rawData.rawCodes() == null ? null : new IngestPolicyCommand.RawCodes(
                        rawData.rawCodes().ageMin(),
                        rawData.rawCodes().ageMax(),
                        rawData.rawCodes().ageLimitYn(),
                        rawData.rawCodes().maritalStatusCd(),
                        rawData.rawCodes().earnConditionCd(),
                        rawData.rawCodes().earnMin(),
                        rawData.rawCodes().earnMax(),
                        rawData.rawCodes().earnEtcCn(),
                        rawData.rawCodes().employmentKindCd(),
                        rawData.rawCodes().educationCd(),
                        rawData.rawCodes().majorFieldCd(),
                        rawData.rawCodes().specializationCd(),
                        rawData.rawCodes().zipCodes() == null ? List.of() : rawData.rawCodes().zipCodes()
                )
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
            Integer referenceYear,
            String supportCycle,
            String provideType,
            String organization,
            String contact,
            List<String> lifeTags,
            List<String> themeTags,
            List<String> targetTags,
            List<@Valid Attachment> attachments,
            List<@Valid ReferenceSite> referenceSites,
            List<@Valid ApplyMethod> applyMethods,
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
            @Valid RawCodes rawCodes
    ) {}

    public record Attachment(
            @NotBlank String name,
            @NotBlank String url,
            String mediaType
    ) {}

    public record ReferenceSite(
            @NotBlank String name,
            @NotBlank String url
    ) {}

    public record ApplyMethod(
            @NotBlank String stageName,
            String description
    ) {}

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
