package com.youthfit.admin.presentation.dto;

import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;

/**
 * Enrichment 검토 후보 목록 행 뷰.
 */
public record CandidateView(
        Long id,
        String title,
        String organization,
        EnrichmentStatus enrichmentStatus,
        Double confidence,
        DetailLevel detailLevel,
        boolean needsReview,
        EnrichmentJobView latestJob
) {
    public static CandidateView of(Policy policy, EnrichmentJob latestJob, boolean needsReview) {
        PolicyEnrichment e = policy.getEnrichment();
        return new CandidateView(
                policy.getId(),
                policy.getTitle(),
                policy.getOrganization(),
                e == null ? null : e.status(),
                e == null ? null : e.confidence(),
                policy.getDetailLevel(),
                needsReview,
                EnrichmentJobView.of(latestJob)
        );
    }
}
