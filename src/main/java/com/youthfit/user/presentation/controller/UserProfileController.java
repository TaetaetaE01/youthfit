package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.application.dto.result.UserProfileResult;
import com.youthfit.user.application.service.UserProfileService;
import com.youthfit.user.presentation.dto.request.UpdateProfileRequest;
import com.youthfit.user.presentation.dto.response.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "사용자 프로필", description = "프로필 조회 및 수정 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회한다")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> findMyProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserProfileResult result = userProfileService.findMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(UserProfileResponse.from(result)));
    }

    @Operation(summary = "내 프로필 수정", description = "로그인한 사용자의 프로필 정보를 수정한다")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        UserProfileResult result = userProfileService.updateMyProfile(userId, request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(UserProfileResponse.from(result)));
    }
}
