package com.youthfit.auth.presentation.controller;

import com.youthfit.auth.presentation.dto.request.KakaoLoginRequest;
import com.youthfit.auth.presentation.dto.request.TokenRefreshRequest;
import com.youthfit.auth.presentation.dto.response.TokenResponse;
import com.youthfit.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "인증", description = "카카오 로그인, 토큰 갱신, 로그아웃 API")
public interface AuthApi {

    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 로그인하고 JWT 토큰을 발급한다")
    ResponseEntity<ApiResponse<TokenResponse>> loginWithKakao(KakaoLoginRequest request);

    @Operation(summary = "토큰 갱신", description = "Refresh 토큰으로 새 Access 토큰을 발급한다")
    ResponseEntity<ApiResponse<TokenResponse>> refreshToken(TokenRefreshRequest request);

    @Operation(summary = "로그아웃", description = "현재 사용자의 Refresh 토큰을 무효화한다")
    ResponseEntity<ApiResponse<Void>> logout(Authentication authentication);
}
