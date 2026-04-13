package com.youthfit.auth.presentation.dto.request;

import com.youthfit.auth.application.dto.command.KakaoLoginCommand;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank(message = "인가 코드는 필수입니다")
        String code
) {
    public KakaoLoginCommand toCommand() {
        return new KakaoLoginCommand(code);
    }
}
