package com.youthfit.auth.infrastructure.oauth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoUserInfo {

    private final String providerId;
    private final String email;
    private final String nickname;
    private final String profileImageUrl;
}
