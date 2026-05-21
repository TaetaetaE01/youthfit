package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyCalendarPageResult;

import java.util.List;

public record PolicyCalendarPageResponse(
        List<PolicyCalendarResponse> content,
        long totalCount,
        int page,
        int size,
        int totalPages,
        boolean hasNext
) {
    public static PolicyCalendarPageResponse from(PolicyCalendarPageResult result) {
        return new PolicyCalendarPageResponse(
                result.items().stream().map(PolicyCalendarResponse::from).toList(),
                result.totalCount(),
                result.page(),
                result.size(),
                result.totalPages(),
                result.hasNext()
        );
    }
}
