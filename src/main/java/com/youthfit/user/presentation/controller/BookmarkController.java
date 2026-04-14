package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.application.dto.result.BookmarkResult;
import com.youthfit.user.application.dto.result.BookmarkWithPolicyResult;
import com.youthfit.user.application.service.BookmarkService;
import com.youthfit.user.presentation.dto.request.CreateBookmarkRequest;
import com.youthfit.user.presentation.dto.response.BookmarkPageResponse;
import com.youthfit.user.presentation.dto.response.BookmarkResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping
    public ResponseEntity<ApiResponse<BookmarkResponse>> createBookmark(
            Authentication authentication,
            @Valid @RequestBody CreateBookmarkRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        BookmarkResult result = bookmarkService.createBookmark(userId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BookmarkResponse.from(result)));
    }

    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<ApiResponse<Void>> deleteBookmark(
            Authentication authentication,
            @PathVariable Long bookmarkId) {
        Long userId = (Long) authentication.getPrincipal();
        bookmarkService.deleteBookmark(userId, bookmarkId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BookmarkPageResponse>> findMyBookmarks(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = (Long) authentication.getPrincipal();
        Page<BookmarkWithPolicyResult> page = bookmarkService.findMyBookmarks(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(BookmarkPageResponse.from(page)));
    }
}
