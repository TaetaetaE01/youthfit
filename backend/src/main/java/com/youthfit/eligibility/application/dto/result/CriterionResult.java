package com.youthfit.eligibility.application.dto.result;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;

public record CriterionResult(
        String field,
        String label,
        EligibilityResult result,
        String reason,
        String sourceReference,
        String confidenceNote
) {

    public static CriterionResult from(CriterionEvaluation evaluation) {
        // TODO Task 8: rebuild this with RequirementFormatter/UserValueFormatter/VerdictTextGenerator.
        // Temporary stub to keep the build green between Task 6 and Task 8.
        return new CriterionResult(
                evaluation.field(),
                evaluation.label(),
                evaluation.result(),
                "",
                evaluation.rule().getSourceReference(),
                null
        );
    }
}
