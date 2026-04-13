package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.application.dto.result.UserProfileResult;
import com.youthfit.user.application.service.UserProfileService;
import com.youthfit.user.presentation.dto.request.UpdateProfileRequest;
import com.youthfit.user.presentation.dto.response.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> findMyProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserProfileResult result = userProfileService.findMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(UserProfileResponse.from(result)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        UserProfileResult result = userProfileService.updateMyProfile(userId, request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(UserProfileResponse.from(result)));
    }
}
