package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.BookmarkIdPairResult;

public record BookmarkIdPairResponse(
        Long bookmarkId,
        Long policyId
) {

    public static BookmarkIdPairResponse from(BookmarkIdPairResult result) {
        return new BookmarkIdPairResponse(result.bookmarkId(), result.policyId());
    }
}
