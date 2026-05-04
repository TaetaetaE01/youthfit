package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.UncertainReason;

public record CriterionEvaluation(
        EligibilityRule rule,
        EligibilityResult result,
        Object userValue,
        UncertainReason uncertainReason
) {

    public String field() {
        return rule.getField();
    }

    public String label() {
        return rule.getLabel();
    }

    public static CriterionEvaluation eligible(EligibilityRule rule, Object userValue) {
        return new CriterionEvaluation(rule, EligibilityResult.LIKELY_ELIGIBLE, userValue, null);
    }

    public static CriterionEvaluation ineligible(EligibilityRule rule, Object userValue) {
        return new CriterionEvaluation(rule, EligibilityResult.LIKELY_INELIGIBLE, userValue, null);
    }

    public static CriterionEvaluation uncertain(EligibilityRule rule) {
        return new CriterionEvaluation(rule, EligibilityResult.UNCERTAIN, null, UncertainReason.MISSING_FIELD);
    }

    public static CriterionEvaluation lowConfidenceUncertain(EligibilityRule rule) {
        return new CriterionEvaluation(rule, EligibilityResult.UNCERTAIN, null, UncertainReason.AMBIGUOUS_SOURCE);
    }
}
