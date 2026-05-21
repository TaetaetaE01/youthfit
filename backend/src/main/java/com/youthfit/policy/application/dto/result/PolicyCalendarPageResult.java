package com.youthfit.policy.application.dto.result;

import java.util.List;

public record PolicyCalendarPageResult(
        List<PolicyCalendarResult> items,
        long totalCount,
        int page,
        int size,
        int totalPages,
        boolean hasNext
) { }
