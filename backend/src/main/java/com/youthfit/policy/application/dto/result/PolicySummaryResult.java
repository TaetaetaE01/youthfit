package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.*;

import java.time.LocalDate;

public record PolicySummaryResult(
        Long id,
        String title,
        String summary,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        PolicyStatus status,
        DetailLevel detailLevel,
        String organization
) {
    public static PolicySummaryResult from(Policy policy) {
        return new PolicySummaryResult(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getCategory(),
                policy.getRegionCode(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getReferenceYear(),
                policy.getStatus(),
                policy.getDetailLevel(),
                policy.getOrganization()
        );
    }
}
