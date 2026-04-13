package com.youthfit.auth.application.dto.result;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResult {

    private final String accessToken;
    private final String refreshToken;
}
