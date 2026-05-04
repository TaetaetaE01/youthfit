package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;

import java.util.Arrays;
import java.util.stream.Collectors;

public class RequirementFormatter {

    public RequirementView format(String field, RuleOperator operator, String value) {
        String displayText = switch (operator) {
            case EQ      -> formatScalar(field, value);
            case NOT_EQ  -> formatScalar(field, value) + " 제외";
            case GTE     -> formatScalar(field, value) + " 이상";
            case LTE     -> formatScalar(field, value) + " 이하";
            case BETWEEN -> formatRange(field, value);
            case IN      -> formatList(field, value);
        };
        return new RequirementView(operator.name(), displayText);
    }

    private String formatScalar(String field, String raw) {
        return switch (field) {
            case "age" -> "만 " + raw + "세";
            case "incomeMin", "incomeMax", "annualIncome" -> formatWon(raw);
            case "maritalStatus" -> safeEnumLabel(MaritalStatus.class, raw);
            case "education", "educationLevel" -> safeEnumLabel(Education.class, raw);
            case "employmentKind", "employmentStatus" -> safeEnumLabel(EmploymentKind.class, raw);
            case "majorField" -> safeEnumLabel(MajorField.class, raw);
            case "specializationField" -> safeEnumLabel(SpecializationField.class, raw);
            default -> raw;
        };
    }

    private String formatRange(String field, String value) {
        String[] bounds = value.split("~");
        String lo = bounds[0].trim();
        String hi = bounds[1].trim();
        return formatScalar(field, lo) + " 이상 " + formatScalar(field, hi).replaceFirst("^만 ", "") + " 이하";
    }

    private String formatList(String field, String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(v -> formatScalar(field, v))
                .collect(Collectors.joining(", "));
    }

    private String formatWon(String raw) {
        try {
            long n = Long.parseLong(raw.trim());
            long manWon = n / 10_000;
            return String.format("%,d만원", manWon);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private <E extends Enum<E>> String safeEnumLabel(Class<E> enumType, String raw) {
        try {
            E value = Enum.valueOf(enumType, raw);
            return invokeDisplayName(value);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    private <E extends Enum<E>> String invokeDisplayName(E value) {
        try {
            return (String) value.getClass().getMethod("displayName").invoke(value);
        } catch (Exception e) {
            return value.name();
        }
    }
}
