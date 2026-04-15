package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.PolicyStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PolicyDetailResponse(
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
    public static PolicyDetailResponse from(PolicyDetailResult result) {
        return new PolicyDetailResponse(
                result.id(),
                result.title(),
                result.summary(),
                result.category(),
                result.regionCode(),
                result.applyStart(),
                result.applyEnd(),
                result.status(),
                result.detailLevel(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
