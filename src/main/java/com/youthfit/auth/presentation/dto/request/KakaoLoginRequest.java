package com.youthfit.auth.presentation.dto.request;

import com.youthfit.auth.application.dto.command.KakaoLoginCommand;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoLoginRequest {

    @NotBlank(message = "인가 코드는 필수입니다")
    private String code;

    public KakaoLoginCommand toCommand() {
        return KakaoLoginCommand.builder()
                .authorizationCode(code)
                .build();
    }
}
