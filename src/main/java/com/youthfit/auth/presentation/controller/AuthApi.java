package com.youthfit.auth.presentation.controller;

import com.youthfit.auth.presentation.dto.request.KakaoLoginRequest;
import com.youthfit.auth.presentation.dto.request.TokenRefreshRequest;
import com.youthfit.auth.presentation.dto.response.TokenResponse;
import com.youthfit.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "인증", description = "카카오 로그인, 토큰 갱신, 로그아웃 API")
public interface AuthApi {

    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 로그인하고 JWT 토큰을 발급한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "카카오 인증에 실패했습니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<ApiResponse<TokenResponse>> loginWithKakao(KakaoLoginRequest request);

    @Operation(summary = "토큰 갱신", description = "Refresh 토큰으로 새 Access 토큰을 발급한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh 토큰입니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<ApiResponse<TokenResponse>> refreshToken(TokenRefreshRequest request);

    @Operation(summary = "로그아웃", description = "현재 사용자의 Refresh 토큰을 무효화한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다 (YF-004)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<Void>> logout(Authentication authentication);
}
