package com.youthfit.policy.application.dto.result;

import java.util.List;

public record PolicyPageResult(
        List<PolicySummaryResult> policies,
        long totalCount,
        int page,
        int size,
        int totalPages,
        boolean hasNext
) {
}
