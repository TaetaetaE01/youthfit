package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.BookmarkWithPolicyResult;
import org.springframework.data.domain.Page;

import java.util.List;

public record BookmarkPageResponse(
        long totalElements,
        int totalPages,
        int currentPage,
        int size,
        List<BookmarkWithPolicyResponse> content
) {

    public static BookmarkPageResponse from(Page<BookmarkWithPolicyResult> page) {
        return new BookmarkPageResponse(
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.getContent().stream()
                        .map(BookmarkWithPolicyResponse::from)
                        .toList()
        );
    }
}
