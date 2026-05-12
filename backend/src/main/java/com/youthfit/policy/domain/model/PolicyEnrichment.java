package com.youthfit.policy.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record PolicyEnrichment(
        String sourceUrl,
        Instant fetchedAt,
        String extractor,
        Double confidence,
        EnrichmentStatus status,
        Sections sections,
        List<ExtraAttachment> extraAttachments
) {
    public static final double EXPOSURE_CONFIDENCE_THRESHOLD = 0.6;

    @JsonIgnore
    public boolean isExposable() {
        return status == EnrichmentStatus.OK
                && confidence != null
                && confidence >= EXPOSURE_CONFIDENCE_THRESHOLD;
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
    ) {
        @JsonCreator
        public Sections(
                @JsonProperty("supportTarget") String supportTarget,
                @JsonProperty("supportContent") String supportContent,
                @JsonProperty("applyMethod") String applyMethod,
                @JsonProperty("requiredDocuments") String requiredDocuments,
                @JsonProperty("deadlineNote") String deadlineNote,
                @JsonProperty("policyOverview") String policyOverview,
                @JsonProperty("eligibilityCriteria") String eligibilityCriteria,
                @JsonProperty("operatingOrganization") String operatingOrganization,
                @JsonProperty("contactPhone") String contactPhone
        ) {
            this.supportTarget = supportTarget;
            this.supportContent = supportContent;
            this.applyMethod = applyMethod;
            this.requiredDocuments = requiredDocuments;
            this.deadlineNote = deadlineNote;
            this.policyOverview = policyOverview;
            this.eligibilityCriteria = eligibilityCriteria;
            this.operatingOrganization = operatingOrganization;
            this.contactPhone = contactPhone;
        }
    }

    public record ExtraAttachment(String name, String url) {}
}
