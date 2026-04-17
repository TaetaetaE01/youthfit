package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.presentation.dto.request.CreateBookmarkRequest;
import com.youthfit.user.presentation.dto.response.BookmarkIdPairResponse;
import com.youthfit.user.presentation.dto.response.BookmarkPageResponse;
import com.youthfit.user.presentation.dto.response.BookmarkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

@Tag(name = "북마크", description = "정책 북마크 등록, 삭제, 목록 조회 API")
public interface BookmarkApi {

    @Operation(summary = "북마크 등록", description = "정책을 북마크에 추가한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정책을 찾을 수 없습니다 (YF-004)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 북마크한 정책입니다 (YF-005)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<BookmarkResponse>> createBookmark(
            Authentication authentication,
            CreateBookmarkRequest request);

    @Operation(summary = "북마크 삭제", description = "북마크를 삭제한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한이 없습니다 (YF-003)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "북마크를 찾을 수 없습니다 (YF-004)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<Void>> deleteBookmark(
            Authentication authentication,
            @Parameter(description = "북마크 ID") Long bookmarkId);

    @Operation(summary = "내 북마크 ID 목록 조회", description = "로그인한 사용자의 북마크/정책 ID 페어 전체를 반환한다. 목록 화면에서 북마크 여부를 빠르게 표시하기 위한 가벼운 응답")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<List<BookmarkIdPairResponse>>> findMyBookmarkIds(
            Authentication authentication);

    @Operation(summary = "내 북마크 목록 조회", description = "로그인한 사용자의 북마크 목록을 페이징 조회한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<BookmarkPageResponse>> findMyBookmarks(
            Authentication authentication,
            Pageable pageable);
}
