package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class EligibilityRuleValidator {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "age", "region", "incomeMin", "incomeMax", "annualIncome",
            "maritalStatus", "employmentKind", "education",
            "majorField", "specializationField"
    );

    public ValidationReport validate(List<RawExtractedRule> rawRules) {
        List<RawExtractedRule> accepted = new ArrayList<>();
        List<String> feedback = new ArrayList<>();

        for (RawExtractedRule rule : rawRules) {
            String error = validateOne(rule);
            if (error != null) {
                feedback.add("폐기: field=" + rule.field()
                        + ", operator=" + rule.operator()
                        + ", value=" + rule.value()
                        + " (" + error + ")");
                continue;
            }
            accepted.add(normalizeConfidence(rule));
        }

        boolean shouldRetry = !rawRules.isEmpty()
                && (accepted.size() * 2 < rawRules.size());

        return new ValidationReport(accepted, feedback, shouldRetry);
    }

    private String validateOne(RawExtractedRule rule) {
        if (rule.field() == null || !ALLOWED_FIELDS.contains(rule.field())) {
            return "허용되지 않은 field: " + rule.field();
        }
        RuleOperator operator;
        try {
            operator = RuleOperator.valueOf(rule.operator());
        } catch (Exception e) {
            return "허용되지 않은 operator: " + rule.operator();
        }
        if (rule.value() == null || rule.value().isBlank()) {
            return "value 누락";
        }
        return validateValue(rule.field(), operator, rule.value().trim());
    }

    private String validateValue(String field, RuleOperator operator, String value) {
        return switch (field) {
            case "age" -> validateAge(operator, value);
            case "region" -> validateRegion(operator, value);
            case "incomeMin", "incomeMax", "annualIncome" -> validateIncome(operator, value);
            case "maritalStatus" -> validateEnum(operator, value, MaritalStatus.class);
            case "employmentKind" -> validateEnum(operator, value, EmploymentKind.class);
            case "education" -> validateEnum(operator, value, Education.class);
            case "majorField" -> validateEnum(operator, value, MajorField.class);
            case "specializationField" -> validateEnum(operator, value, SpecializationField.class);
            default -> "지원되지 않는 field 매핑: " + field;
        };
    }

    private String validateAge(RuleOperator operator, String value) {
        if (operator == RuleOperator.BETWEEN) {
            String[] bounds = value.split("~");
            if (bounds.length != 2) return "BETWEEN 형식 위반 (예: '19~34')";
            try {
                int min = Integer.parseInt(bounds[0].trim());
                int max = Integer.parseInt(bounds[1].trim());
                if (min < 0 || max > 100 || min > max) {
                    return "age 범위 위반 (0~100, min<=max)";
                }
                return null;
            } catch (NumberFormatException e) {
                return "BETWEEN 정수 변환 실패";
            }
        }
        if (operator == RuleOperator.GTE || operator == RuleOperator.LTE
                || operator == RuleOperator.EQ || operator == RuleOperator.NOT_EQ) {
            try {
                int n = Integer.parseInt(value);
                if (n < 0 || n > 100) return "age 범위 위반 (0~100)";
                return null;
            } catch (NumberFormatException e) {
                return "정수 변환 실패";
            }
        }
        return "age 에 IN 오퍼레이터는 부적합";
    }

    private String validateRegion(RuleOperator operator, String value) {
        if (operator == RuleOperator.EQ || operator == RuleOperator.NOT_EQ) {
            return value.isBlank() ? "region 값 비어있음" : null;
        }
        if (operator == RuleOperator.IN) {
            String[] codes = value.split(",");
            for (String c : codes) {
                if (c.trim().isBlank()) return "region IN 멤버 비어있음";
            }
            return null;
        }
        return "region 에 GTE/LTE/BETWEEN 부적합";
    }

    private String validateIncome(RuleOperator operator, String value) {
        if (operator == RuleOperator.BETWEEN) {
            String[] bounds = value.split("~");
            if (bounds.length != 2) return "BETWEEN 형식 위반";
            try {
                long min = Long.parseLong(bounds[0].trim());
                long max = Long.parseLong(bounds[1].trim());
                if (min < 0 || max < 0 || min > max) return "소득 범위 위반";
                return null;
            } catch (NumberFormatException e) {
                return "소득 BETWEEN 정수 변환 실패";
            }
        }
        if (operator == RuleOperator.GTE || operator == RuleOperator.LTE
                || operator == RuleOperator.EQ || operator == RuleOperator.NOT_EQ) {
            try {
                long n = Long.parseLong(value);
                if (n < 0) return "소득은 음수 불가";
                return null;
            } catch (NumberFormatException e) {
                return "소득 정수 변환 실패";
            }
        }
        return "소득 필드에 IN 오퍼레이터 부적합";
    }

    private <E extends Enum<E>> String validateEnum(RuleOperator operator, String value, Class<E> enumType) {
        if (operator == RuleOperator.IN) {
            String[] members = value.split(",");
            for (String m : members) {
                if (!isEnumMember(enumType, m.trim())) {
                    return enumType.getSimpleName() + " 비허용 멤버: " + m.trim();
                }
            }
            return null;
        }
        if (operator == RuleOperator.EQ || operator == RuleOperator.NOT_EQ) {
            return isEnumMember(enumType, value)
                    ? null
                    : enumType.getSimpleName() + " 비허용 값: " + value;
        }
        return enumType.getSimpleName() + " 에 GTE/LTE/BETWEEN 부적합";
    }

    private <E extends Enum<E>> boolean isEnumMember(Class<E> enumType, String value) {
        try {
            Enum.valueOf(enumType, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private RawExtractedRule normalizeConfidence(RawExtractedRule rule) {
        String c = rule.confidence();
        if (c == null) return withConfidence(rule, "MEDIUM");
        try {
            RuleConfidence.valueOf(c);
            return rule;
        } catch (Exception e) {
            return withConfidence(rule, "MEDIUM");
        }
    }

    private RawExtractedRule withConfidence(RawExtractedRule rule, String confidence) {
        return new RawExtractedRule(
                rule.field(), rule.operator(), rule.value(),
                rule.label(), rule.sourceReference(), confidence);
    }

    public record ValidationReport(
            List<RawExtractedRule> acceptedRules,
            List<String> feedbackMessages,
            boolean shouldRetry
    ) {
    }
}
