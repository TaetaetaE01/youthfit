package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.user.domain.model.User;

import java.util.Set;

public class EligibilityEvaluator {

    public CriterionEvaluation evaluateRule(EligibilityRule rule, User user) {
        Object userValue = extractFieldValue(user, rule.getField());
        if (userValue == null) {
            return CriterionEvaluation.uncertain(rule);
        }
        boolean matched = evaluateOperator(rule.getOperator(), userValue, rule.getValue());
        return matched
                ? CriterionEvaluation.eligible(rule, userValue)
                : CriterionEvaluation.ineligible(rule, userValue);
    }

    private Object extractFieldValue(User user, String field) {
        return switch (field) {
            case "age" -> user.getAge();
            case "region" -> user.getRegion();
            case "annualIncome" -> user.getAnnualIncome();
            case "employmentStatus" -> user.getEmploymentStatus() != null
                    ? user.getEmploymentStatus().name() : null;
            case "educationLevel" -> user.getEducationLevel() != null
                    ? user.getEducationLevel().name() : null;
            case "householdSize" -> user.getHouseholdSize();
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
