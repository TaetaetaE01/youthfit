package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.BookmarkResult;

import java.time.LocalDateTime;

public record BookmarkResponse(
        Long bookmarkId,
        Long policyId,
        LocalDateTime createdAt
) {

    public static BookmarkResponse from(BookmarkResult result) {
        return new BookmarkResponse(
                result.bookmarkId(),
                result.policyId(),
                result.createdAt()
        );
    }
}
