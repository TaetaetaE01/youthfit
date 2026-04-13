package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PolicyDetailResult(
        Long id,
        String title,
        String summary,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        PolicyStatus status,
        DetailLevel detailLevel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PolicyDetailResult from(Policy policy) {
        return new PolicyDetailResult(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getCategory(),
                policy.getRegionCode(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getStatus(),
                policy.getDetailLevel(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
