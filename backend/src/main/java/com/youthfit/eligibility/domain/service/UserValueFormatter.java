package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.view.UserValueView;
import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.LabeledEnum;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;

/**
 * 사용자가 가진 raw 값을 사용자에게 보여줄 한국어 displayText 로 변환한다.
 * 프레임워크 의존이 없는 순수 도메인 서비스이며 stateless·thread-safe 하다.
 */
public class UserValueFormatter {

    public UserValueView format(String field, Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String raw = String.valueOf(rawValue);
        String display = switch (field) {
            case "age" -> "만 " + raw + "세";
            case "incomeMin", "incomeMax", "annualIncome" -> formatWon(raw);
            case "maritalStatus" -> safeEnumLabel(MaritalStatus.class, raw);
            case "education", "educationLevel" -> safeEnumLabel(Education.class, raw);
            case "employmentKind", "employmentStatus" -> safeEnumLabel(EmploymentKind.class, raw);
            case "majorField" -> safeEnumLabel(MajorField.class, raw);
            case "specializationField" -> safeEnumLabel(SpecializationField.class, raw);
            default -> raw;
        };
        return new UserValueView(raw, display);
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
