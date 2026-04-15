package com.youthfit.auth.application.dto.result;

public record TokenResult(
        String accessToken,
        String refreshToken
) {
}
