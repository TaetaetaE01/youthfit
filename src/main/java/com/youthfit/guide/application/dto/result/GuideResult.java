package com.youthfit.guide.application.dto.result;

import com.youthfit.guide.domain.model.Guide;

import java.time.LocalDateTime;

public record GuideResult(
        Long id,
        Long policyId,
        String summaryHtml,
        String sourceHash,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static GuideResult from(Guide guide) {
        return new GuideResult(
                guide.getId(),
                guide.getPolicyId(),
                guide.getSummaryHtml(),
                guide.getSourceHash(),
                guide.getCreatedAt(),
                guide.getUpdatedAt()
        );
    }
}
