package com.youthfit.policy.application.dto.result;

public record PolicyIngestionResult(
        Long policyId,
        boolean isNew
) {
}
