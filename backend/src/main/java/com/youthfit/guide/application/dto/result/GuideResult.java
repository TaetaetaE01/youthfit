package com.youthfit.guide.application.dto.result;

import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;

import java.time.LocalDateTime;

public record GuideResult(
        Long id,
        Long policyId,
        GuideContent content,
        String sourceHash,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static GuideResult from(Guide guide) {
        return new GuideResult(
                guide.getId(),
                guide.getPolicyId(),
                guide.getContent(),
                guide.getSourceHash(),
                guide.getCreatedAt(),
                guide.getUpdatedAt()
        );
    }
}
