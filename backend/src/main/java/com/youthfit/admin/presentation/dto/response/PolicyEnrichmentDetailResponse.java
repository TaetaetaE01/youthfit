package com.youthfit.admin.presentation.dto.response;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.policy.domain.model.SkipReason;

import java.time.Instant;
import java.util.List;

/**
 * 단일 정책의 enrichment 상세 응답 DTO.
 */
public record PolicyEnrichmentDetailResponse(
        Long policyId,
        String title,
        EnrichmentResponse enrichment,
        DetailLevel detailLevel,
        List<UrlResponse> referenceSites,
        List<EnrichmentJobResponse> recentJobs,
        List<AttachmentResponse> attachments,
        boolean needsReview
) {
    /**
     * 첨부 다운로드·추출 결과. status=FAILED 면 error 에, SKIPPED 면 skipReason 에 사유가 담긴다.
     */
    public record AttachmentResponse(
            Long id,
            String name,
            String mediaType,
            AttachmentStatus status,
            SkipReason skipReason,
            String error,
            int retryCount,
            boolean hasText
    ) {
        public static AttachmentResponse of(PolicyAttachment a) {
            String text = a.getExtractedText();
            return new AttachmentResponse(
                    a.getId(),
                    a.getName(),
                    a.getMediaType(),
                    a.getExtractionStatus(),
                    a.getSkipReason(),
                    a.getExtractionError(),
                    a.getExtractionRetryCount(),
                    text != null && !text.isBlank()
            );
        }
    }

    public record EnrichmentResponse(
            EnrichmentStatus status,
            Double confidence,
            Instant fetchedAt,
            String extractor,
            SectionsResponse sections
    ) {
        public static EnrichmentResponse of(PolicyEnrichment e) {
            if (e == null) return null;
            return new EnrichmentResponse(
                    e.status(),
                    e.confidence(),
                    e.fetchedAt(),
                    e.extractor(),
                    SectionsResponse.of(e.sections())
            );
        }
    }

    public record SectionsResponse(
            String supportTarget,
            String supportContent,
            String eligibilityCriteria,
            String applyMethod,
            String requiredDocuments,
            String deadlineNote,
            String policyOverview,
            String operatingOrganization,
            String contactPhone
    ) {
        public static SectionsResponse of(PolicyEnrichment.Sections s) {
            if (s == null) return null;
            return new SectionsResponse(
                    s.supportTarget(),
                    s.supportContent(),
                    s.eligibilityCriteria(),
                    s.applyMethod(),
                    s.requiredDocuments(),
                    s.deadlineNote(),
                    s.policyOverview(),
                    s.operatingOrganization(),
                    s.contactPhone()
            );
        }
    }

    public static PolicyEnrichmentDetailResponse of(Policy policy, List<EnrichmentJob> recentJobs,
                                                    boolean needsReview, List<PolicyAttachment> attachments) {
        List<UrlResponse> sites = policy.getReferenceSites() == null
                ? List.of()
                : policy.getReferenceSites().stream().map(UrlResponse::of).toList();
        List<EnrichmentJobResponse> jobs = recentJobs == null
                ? List.of()
                : recentJobs.stream().map(EnrichmentJobResponse::of).toList();
        List<AttachmentResponse> atts = attachments == null
                ? List.of()
                : attachments.stream().map(AttachmentResponse::of).toList();
        return new PolicyEnrichmentDetailResponse(
                policy.getId(),
                policy.getTitle(),
                EnrichmentResponse.of(policy.getEnrichment()),
                policy.getDetailLevel(),
                sites,
                jobs,
                atts,
                needsReview
        );
    }
}
