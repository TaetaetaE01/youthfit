package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;

public record CriterionEvaluation(
        String field,
        String label,
        EligibilityResult result,
        String reason,
        String sourceReference
) {

    public static CriterionEvaluation eligible(EligibilityRule rule, Object userValue) {
        String reason = formatValue(userValue) + " — " + rule.getLabel() + "(" + rule.getValue() + ") 충족";
        return new CriterionEvaluation(
                rule.getField(), rule.getLabel(), EligibilityResult.LIKELY_ELIGIBLE,
                reason, rule.getSourceReference()
        );
    }

    public static CriterionEvaluation ineligible(EligibilityRule rule, Object userValue) {
        String reason = formatValue(userValue) + " — " + rule.getLabel() + "(" + rule.getValue() + ") 미충족";
        return new CriterionEvaluation(
                rule.getField(), rule.getLabel(), EligibilityResult.LIKELY_INELIGIBLE,
                reason, rule.getSourceReference()
        );
    }

    public static CriterionEvaluation uncertain(EligibilityRule rule) {
        String reason = "정보 미입력 — 판단 불가";
        return new CriterionEvaluation(
                rule.getField(), rule.getLabel(), EligibilityResult.UNCERTAIN,
                reason, rule.getSourceReference()
        );
    }

    private static String formatValue(Object value) {
        return String.valueOf(value);
    }
}
