package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.application.dto.result.EligibilityProfileResult;
import com.youthfit.user.application.service.EligibilityProfileService;
import com.youthfit.user.presentation.dto.request.UpdateEligibilityProfileRequest;
import com.youthfit.user.presentation.dto.response.EligibilityProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/eligibility-profile")
@RequiredArgsConstructor
public class EligibilityProfileController implements EligibilityProfileApi {

    private final EligibilityProfileService eligibilityProfileService;

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<EligibilityProfileResponse>> findMyEligibilityProfile(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        EligibilityProfileResult result = eligibilityProfileService.findMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(EligibilityProfileResponse.from(result)));
    }

    @PatchMapping
    @Override
    public ResponseEntity<ApiResponse<EligibilityProfileResponse>> updateMyEligibilityProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateEligibilityProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        EligibilityProfileResult result = eligibilityProfileService.updateMyProfile(userId, request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(EligibilityProfileResponse.from(result)));
    }
}
