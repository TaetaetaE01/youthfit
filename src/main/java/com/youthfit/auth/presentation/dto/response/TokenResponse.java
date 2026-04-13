package com.youthfit.auth.presentation.dto.response;

import com.youthfit.auth.application.dto.result.TokenResult;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponse {

    private final String accessToken;
    private final String refreshToken;

    public static TokenResponse from(TokenResult result) {
        return TokenResponse.builder()
                .accessToken(result.getAccessToken())
                .refreshToken(result.getRefreshToken())
                .build();
    }
}
