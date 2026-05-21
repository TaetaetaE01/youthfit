package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;

import java.util.Map;

public record EnrichmentReviewSummaryResult(
        long total,
        long needsReview,
        Map<EnrichmentStatus, Long> byStatus,
        Map<DetailLevel, Long> byDetailLevel
) { }
