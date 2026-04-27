package com.youthfit.guide.presentation.dto.response;

import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.domain.model.GuideContent;

import java.time.LocalDateTime;

public record GuideResponse(
        Long id,
        Long policyId,
        GuideContent content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static GuideResponse from(GuideResult result) {
        return new GuideResponse(
                result.id(),
                result.policyId(),
                result.content(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
