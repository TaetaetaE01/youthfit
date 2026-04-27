package com.youthfit.guide.presentation.dto.response;

import com.youthfit.guide.application.dto.result.GuideGenerationResult;

public record GuideGenerationResponse(
        Long policyId,
        boolean regenerated,
        String message
) {

    public static GuideGenerationResponse from(GuideGenerationResult result) {
        return new GuideGenerationResponse(
                result.policyId(),
                result.regenerated(),
                result.message()
        );
    }
}
