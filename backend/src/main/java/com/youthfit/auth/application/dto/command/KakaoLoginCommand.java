package com.youthfit.auth.application.dto.command;

public record KakaoLoginCommand(
        String authorizationCode
) {
}
