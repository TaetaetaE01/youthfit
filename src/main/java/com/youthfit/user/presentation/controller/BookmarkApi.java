package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.presentation.dto.request.CreateBookmarkRequest;
import com.youthfit.user.presentation.dto.response.BookmarkPageResponse;
import com.youthfit.user.presentation.dto.response.BookmarkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "북마크", description = "정책 북마크 등록, 삭제, 목록 조회 API")
public interface BookmarkApi {

    @Operation(summary = "북마크 등록", description = "정책을 북마크에 추가한다")
    ResponseEntity<ApiResponse<BookmarkResponse>> createBookmark(
            Authentication authentication,
            CreateBookmarkRequest request);

    @Operation(summary = "북마크 삭제", description = "북마크를 삭제한다")
    ResponseEntity<ApiResponse<Void>> deleteBookmark(
            Authentication authentication,
            @Parameter(description = "북마크 ID") Long bookmarkId);

    @Operation(summary = "내 북마크 목록 조회", description = "로그인한 사용자의 북마크 목록을 페이징 조회한다")
    ResponseEntity<ApiResponse<BookmarkPageResponse>> findMyBookmarks(
            Authentication authentication,
            Pageable pageable);
}
