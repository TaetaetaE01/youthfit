package com.youthfit.admin.presentation.dto.response;

import com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;

import java.util.Map;

/**
 * Enrichment 검토 후보 현황 요약 응답 DTO.
 */
public record EnrichmentCandidateSummaryResponse(
        long total,
        long needsReview,
        Map<EnrichmentStatus, Long> byStatus,
        Map<DetailLevel, Long> byDetailLevel
) {
    public static EnrichmentCandidateSummaryResponse of(EnrichmentReviewSummaryResult r) {
        if (r == null) {
            return new EnrichmentCandidateSummaryResponse(0L, 0L, Map.of(), Map.of());
        }
        return new EnrichmentCandidateSummaryResponse(
                r.total(),
                r.needsReview(),
                r.byStatus() == null ? Map.of() : r.byStatus(),
                r.byDetailLevel() == null ? Map.of() : r.byDetailLevel()
        );
    }
}
