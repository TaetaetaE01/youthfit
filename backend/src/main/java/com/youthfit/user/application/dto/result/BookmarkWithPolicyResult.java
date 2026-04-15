package com.youthfit.user.application.dto.result;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.domain.model.Bookmark;

import java.time.LocalDateTime;

public record BookmarkWithPolicyResult(
        Long bookmarkId,
        PolicySummary policy,
        LocalDateTime createdAt
) {

    public record PolicySummary(
            Long id,
            String title,
            String category,
            String status,
            String applyEnd
    ) {

        public static PolicySummary from(Policy policy) {
            return new PolicySummary(
                    policy.getId(),
                    policy.getTitle(),
                    policy.getCategory().name(),
                    policy.getStatus().name(),
                    policy.getApplyEnd() != null ? policy.getApplyEnd().toString() : null
            );
        }
    }

    public static BookmarkWithPolicyResult of(Bookmark bookmark, Policy policy) {
        return new BookmarkWithPolicyResult(
                bookmark.getId(),
                PolicySummary.from(policy),
                bookmark.getCreatedAt()
        );
    }
}
