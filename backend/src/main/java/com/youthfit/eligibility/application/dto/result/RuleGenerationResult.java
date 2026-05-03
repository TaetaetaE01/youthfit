package com.youthfit.eligibility.application.dto.result;

public record RuleGenerationResult(
        Long policyId,
        boolean generated,
        String message
) {
}
