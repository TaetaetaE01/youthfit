package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.LabeledEnum;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 정책 룰의 (field, operator, value) 를 사용자에게 보여줄 한국어 displayText 로 변환한다.
 * 프레임워크 의존이 없는 순수 도메인 서비스이며 stateless·thread-safe 하다.
 */
public class RequirementFormatter {

    public RequirementView format(String field, RuleOperator operator, String value) {
        String displayText = switch (operator) {
            case EQ      -> formatScalar(field, value);
            case NOT_EQ  -> formatScalar(field, value) + " 제외";
            case GTE     -> formatScalar(field, value) + " 이상";
            case LTE     -> formatScalar(field, value) + " 이하";
            case BETWEEN -> formatRange(field, value);
            case IN      -> formatList(field, value);
            case ANY     -> "제한 없음";
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
        if (bounds.length < 2) {
            return value;
        }
        String lo = bounds[0].trim();
        String hi = bounds[1].trim();
        if ("age".equals(field)) {
            return "만 " + lo + "세 이상 " + hi + "세 이하";
        }
        return formatScalar(field, lo) + " 이상 " + formatScalar(field, hi) + " 이하";
    }

    private String formatList(String field, String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
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

    private <E extends Enum<E> & LabeledEnum> String safeEnumLabel(Class<E> enumType, String raw) {
        try {
            return Enum.valueOf(enumType, raw).displayName();
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }
}
