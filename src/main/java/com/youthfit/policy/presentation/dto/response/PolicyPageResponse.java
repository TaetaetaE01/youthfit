package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyPageResult;

import java.util.List;

public record PolicyPageResponse(
        List<PolicySummaryResponse> content,
        long totalCount,
        int page,
        int size,
        int totalPages,
        boolean hasNext
) {
    public static PolicyPageResponse from(PolicyPageResult result) {
        return new PolicyPageResponse(
                result.policies().stream().map(PolicySummaryResponse::from).toList(),
                result.totalCount(),
                result.page(),
                result.size(),
                result.totalPages(),
                result.hasNext()
        );
    }
}
