package com.youthfit.admin.presentation.dto.response;

import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;

/**
 * Enrichment 검토 후보 목록 행 응답 DTO.
 */
public record EnrichmentCandidateResponse(
        Long id,
        String title,
        String organization,
        EnrichmentStatus enrichmentStatus,
        Double confidence,
        DetailLevel detailLevel,
        boolean needsReview,
        EnrichmentJobResponse latestJob
) {
    public static EnrichmentCandidateResponse of(Policy policy, EnrichmentJob latestJob, boolean needsReview) {
        PolicyEnrichment e = policy.getEnrichment();
        return new EnrichmentCandidateResponse(
                policy.getId(),
                policy.getTitle(),
                policy.getOrganization(),
                e == null ? null : e.status(),
                e == null ? null : e.confidence(),
                policy.getDetailLevel(),
                needsReview,
                EnrichmentJobResponse.of(latestJob)
        );
    }
}
