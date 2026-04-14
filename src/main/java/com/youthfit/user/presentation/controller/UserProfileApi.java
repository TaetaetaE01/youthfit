package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.presentation.dto.request.UpdateProfileRequest;
import com.youthfit.user.presentation.dto.response.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "사용자 프로필", description = "프로필 조회 및 수정 API")
public interface UserProfileApi {

    @Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회한다")
    ResponseEntity<ApiResponse<UserProfileResponse>> findMyProfile(Authentication authentication);

    @Operation(summary = "내 프로필 수정", description = "로그인한 사용자의 프로필 정보를 수정한다")
    ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            Authentication authentication,
            UpdateProfileRequest request);
}
