package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.application.dto.result.BookmarkResult;
import com.youthfit.user.application.dto.result.BookmarkWithPolicyResult;
import com.youthfit.user.application.service.BookmarkService;
import com.youthfit.user.presentation.dto.request.CreateBookmarkRequest;
import com.youthfit.user.presentation.dto.response.BookmarkPageResponse;
import com.youthfit.user.presentation.dto.response.BookmarkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "북마크", description = "정책 북마크 등록, 삭제, 목록 조회 API")
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "북마크 등록", description = "정책을 북마크에 추가한다")
    @PostMapping
    public ResponseEntity<ApiResponse<BookmarkResponse>> createBookmark(
            Authentication authentication,
            @Valid @RequestBody CreateBookmarkRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        BookmarkResult result = bookmarkService.createBookmark(userId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BookmarkResponse.from(result)));
    }

    @Operation(summary = "북마크 삭제", description = "북마크를 삭제한다")
    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<ApiResponse<Void>> deleteBookmark(
            Authentication authentication,
            @Parameter(description = "북마크 ID") @PathVariable Long bookmarkId) {
        Long userId = (Long) authentication.getPrincipal();
        bookmarkService.deleteBookmark(userId, bookmarkId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "내 북마크 목록 조회", description = "로그인한 사용자의 북마크 목록을 페이징 조회한다")
    @GetMapping
    public ResponseEntity<ApiResponse<BookmarkPageResponse>> findMyBookmarks(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = (Long) authentication.getPrincipal();
        Page<BookmarkWithPolicyResult> page = bookmarkService.findMyBookmarks(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(BookmarkPageResponse.from(page)));
    }
}
