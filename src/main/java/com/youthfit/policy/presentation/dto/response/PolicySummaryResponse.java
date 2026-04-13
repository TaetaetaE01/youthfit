package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.PolicyStatus;

import java.time.LocalDate;

public record PolicySummaryResponse(
        Long id,
        String title,
        String summary,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        PolicyStatus status,
        DetailLevel detailLevel
) {
    public static PolicySummaryResponse from(PolicySummaryResult result) {
        return new PolicySummaryResponse(
                result.id(),
                result.title(),
                result.summary(),
                result.category(),
                result.regionCode(),
                result.applyStart(),
                result.applyEnd(),
                result.status(),
                result.detailLevel()
        );
    }
}
