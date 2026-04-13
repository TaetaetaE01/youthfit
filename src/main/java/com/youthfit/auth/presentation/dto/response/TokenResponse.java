package com.youthfit.auth.presentation.dto.response;

import com.youthfit.auth.application.dto.result.TokenResult;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
    public static TokenResponse from(TokenResult result) {
        return new TokenResponse(result.accessToken(), result.refreshToken());
    }
}
