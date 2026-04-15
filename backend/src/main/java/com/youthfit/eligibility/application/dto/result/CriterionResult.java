package com.youthfit.eligibility.application.dto.result;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;

public record CriterionResult(
        String field,
        String label,
        EligibilityResult result,
        String reason,
        String sourceReference
) {

    public static CriterionResult from(CriterionEvaluation evaluation) {
        return new CriterionResult(
                evaluation.field(),
                evaluation.label(),
                evaluation.result(),
                evaluation.reason(),
                evaluation.sourceReference()
        );
    }
}
