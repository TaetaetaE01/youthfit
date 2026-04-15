package com.youthfit.guide.presentation.dto.response;

import com.youthfit.guide.application.dto.result.GuideResult;

import java.time.LocalDateTime;

public record GuideResponse(
        Long id,
        Long policyId,
        String summaryHtml,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static GuideResponse from(GuideResult result) {
        return new GuideResponse(
                result.id(),
                result.policyId(),
                result.summaryHtml(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
