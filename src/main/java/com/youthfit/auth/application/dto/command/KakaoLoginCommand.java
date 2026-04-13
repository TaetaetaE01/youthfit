package com.youthfit.auth.application.dto.command;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoLoginCommand {

    private final String authorizationCode;
}
