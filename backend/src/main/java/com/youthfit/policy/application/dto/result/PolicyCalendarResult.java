package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;

import java.time.LocalDate;

public record PolicyCalendarResult(
        Long id,
        String title,
        Category category,
        LocalDate applyStart,
        LocalDate applyEnd,
        String regionLabel
) {
    public static PolicyCalendarResult from(Policy policy) {
        return new PolicyCalendarResult(
                policy.getId(),
                policy.getTitle(),
                policy.getCategory(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getRegionCode()
        );
    }
}
