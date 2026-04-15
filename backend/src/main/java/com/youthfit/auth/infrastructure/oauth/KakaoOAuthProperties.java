package com.youthfit.auth.infrastructure.oauth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "oauth.kakao")
public class KakaoOAuthProperties {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
}
