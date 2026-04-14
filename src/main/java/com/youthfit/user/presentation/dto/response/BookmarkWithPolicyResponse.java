package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.BookmarkWithPolicyResult;

import java.time.LocalDateTime;

public record BookmarkWithPolicyResponse(
        Long bookmarkId,
        PolicySummaryResponse policy,
        LocalDateTime createdAt
) {

    public record PolicySummaryResponse(
            Long id,
            String title,
            String category,
            String status,
            String applyEnd
    ) {

        public static PolicySummaryResponse from(BookmarkWithPolicyResult.PolicySummary summary) {
            return new PolicySummaryResponse(
                    summary.id(),
                    summary.title(),
                    summary.category(),
                    summary.status(),
                    summary.applyEnd()
            );
        }
    }

    public static BookmarkWithPolicyResponse from(BookmarkWithPolicyResult result) {
        return new BookmarkWithPolicyResponse(
                result.bookmarkId(),
                PolicySummaryResponse.from(result.policy()),
                result.createdAt()
        );
    }
}
