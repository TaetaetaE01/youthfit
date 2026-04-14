package com.youthfit.auth.presentation.controller;

import com.youthfit.auth.application.dto.result.TokenResult;
import com.youthfit.auth.application.service.AuthService;
import com.youthfit.auth.presentation.dto.request.KakaoLoginRequest;
import com.youthfit.auth.presentation.dto.request.TokenRefreshRequest;
import com.youthfit.auth.presentation.dto.response.TokenResponse;
import com.youthfit.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "카카오 로그인, 토큰 갱신, 로그아웃 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 로그인하고 JWT 토큰을 발급한다")
    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<TokenResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request) {
        TokenResult result = authService.loginWithKakao(request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse.from(result)));
    }

    @Operation(summary = "토큰 갱신", description = "Refresh 토큰으로 새 Access 토큰을 발급한다")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            @Valid @RequestBody TokenRefreshRequest request) {
        TokenResult result = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse.from(result)));
    }

    @Operation(summary = "로그아웃", description = "현재 사용자의 Refresh 토큰을 무효화한다")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
