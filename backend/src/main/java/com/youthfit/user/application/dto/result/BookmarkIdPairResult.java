package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.Bookmark;

public record BookmarkIdPairResult(
        Long bookmarkId,
        Long policyId
) {

    public static BookmarkIdPairResult from(Bookmark bookmark) {
        return new BookmarkIdPairResult(bookmark.getId(), bookmark.getPolicyId());
    }
}
