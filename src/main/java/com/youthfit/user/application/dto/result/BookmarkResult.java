package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.Bookmark;

import java.time.LocalDateTime;

public record BookmarkResult(
        Long bookmarkId,
        Long policyId,
        LocalDateTime createdAt
) {

    public static BookmarkResult from(Bookmark bookmark) {
        return new BookmarkResult(
                bookmark.getId(),
                bookmark.getPolicyId(),
                bookmark.getCreatedAt()
        );
    }
}
