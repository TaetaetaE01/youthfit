package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyCalendarResult;
import com.youthfit.policy.domain.model.Category;

import java.time.LocalDate;

public record PolicyCalendarResponse(
        Long id,
        String title,
        Category category,
        LocalDate applyStart,
        LocalDate applyEnd,
        String regionLabel
) {
    public static PolicyCalendarResponse from(PolicyCalendarResult result) {
        return new PolicyCalendarResponse(
                result.id(),
                result.title(),
                result.category(),
                result.applyStart(),
                result.applyEnd(),
                result.regionLabel()
        );
    }
}
