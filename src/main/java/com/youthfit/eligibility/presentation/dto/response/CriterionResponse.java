package com.youthfit.eligibility.presentation.dto.response;

import com.youthfit.eligibility.application.dto.result.CriterionResult;

public record CriterionResponse(
        String field,
        String label,
        String result,
        String reason,
        String sourceReference
) {

    public static CriterionResponse from(CriterionResult criterionResult) {
        return new CriterionResponse(
                criterionResult.field(),
                criterionResult.label(),
                criterionResult.result().name(),
                criterionResult.reason(),
                criterionResult.sourceReference()
        );
    }
}
