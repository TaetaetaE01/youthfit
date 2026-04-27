package com.youthfit.guide.application.dto.result;

public record GuideGenerationResult(
        Long policyId,
        boolean regenerated,
        String message
) {
}
