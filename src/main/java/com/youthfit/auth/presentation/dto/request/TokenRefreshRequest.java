package com.youthfit.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다")
        String refreshToken
) {
}
