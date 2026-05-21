package com.youthfit.policy.application.dto.command;

import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;

import java.util.Set;

public record EnrichmentReviewFilterCommand(
        Boolean needsReviewOnly,
        Set<EnrichmentStatus> statuses,
        Set<DetailLevel> detailLevels,
        String keyword
) { }
