package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.presentation.dto.request.UpdateProfileRequest;
import com.youthfit.user.presentation.dto.response.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "사용자 프로필", description = "프로필 조회 및 수정 API")
public interface UserProfileApi {

    @Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다 (YF-004)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<UserProfileResponse>> findMyProfile(Authentication authentication);

    @Operation(summary = "내 프로필 수정", description = "로그인한 사용자의 프로필 정보를 수정한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다 (YF-004)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            Authentication authentication,
            UpdateProfileRequest request);
}
