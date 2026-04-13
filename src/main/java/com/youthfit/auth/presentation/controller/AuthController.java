package com.youthfit.auth.presentation.controller;

import com.youthfit.auth.application.dto.result.TokenResult;
import com.youthfit.auth.application.service.AuthService;
import com.youthfit.auth.presentation.dto.request.KakaoLoginRequest;
import com.youthfit.auth.presentation.dto.request.TokenRefreshRequest;
import com.youthfit.auth.presentation.dto.response.TokenResponse;
import com.youthfit.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<TokenResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request) {
        TokenResult result = authService.loginWithKakao(request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse.from(result)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            @Valid @RequestBody TokenRefreshRequest request) {
        TokenResult result = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse.from(result)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
