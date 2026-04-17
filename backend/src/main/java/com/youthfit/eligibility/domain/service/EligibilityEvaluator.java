package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.user.domain.model.EligibilityProfile;

import java.util.Set;

public class EligibilityEvaluator {

    public CriterionEvaluation evaluateRule(EligibilityRule rule, EligibilityProfile profile) {
        Object userValue = extractFieldValue(profile, rule.getField());
        if (userValue == null) {
            return CriterionEvaluation.uncertain(rule);
        }
        boolean matched = evaluateOperator(rule.getOperator(), userValue, rule.getValue());
        return matched
                ? CriterionEvaluation.eligible(rule, userValue)
                : CriterionEvaluation.ineligible(rule, userValue);
    }

    private Object extractFieldValue(EligibilityProfile profile, String field) {
        return switch (field) {
            case "age" -> profile.getAge();
            case "region", "legalDongCode" -> profile.getLegalDongCode();
            case "incomeMin" -> profile.getIncomeMin();
            case "incomeMax" -> profile.getIncomeMax();
            case "annualIncome" -> profile.getIncomeMax() != null
                    ? profile.getIncomeMax() : profile.getIncomeMin();
            case "maritalStatus" -> profile.getMaritalStatus() != null
                    ? profile.getMaritalStatus().name() : null;
            case "employmentKind", "employmentStatus" -> profile.getEmploymentKind() != null
                    ? profile.getEmploymentKind().name() : null;
            case "education", "educationLevel" -> profile.getEducation() != null
                    ? profile.getEducation().name() : null;
            case "majorField" -> profile.getMajorField() != null
                    ? profile.getMajorField().name() : null;
            case "specializationField" -> profile.getSpecializationField() != null
                    ? profile.getSpecializationField().name() : null;
            default -> null;
        };
    }

    private boolean evaluateOperator(RuleOperator operator, Object userValue, String ruleValue) {
        return switch (operator) {
            case EQ -> userValue.toString().equals(ruleValue);
            case NOT_EQ -> !userValue.toString().equals(ruleValue);
            case GTE -> toNumber(userValue) >= toNumber(ruleValue);
            case LTE -> toNumber(userValue) <= toNumber(ruleValue);
            case IN -> {
                Set<String> allowed = Set.of(ruleValue.split(","));
                yield allowed.contains(userValue.toString());
            }
            case BETWEEN -> {
                String[] bounds = ruleValue.split("~");
                long val = toNumber(userValue);
                yield val >= Long.parseLong(bounds[0].trim()) && val <= Long.parseLong(bounds[1].trim());
            }
        };
    }

    private long toNumber(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString().trim());
    }
}
