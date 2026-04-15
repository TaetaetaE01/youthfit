package com.youthfit.auth.infrastructure.oauth;

import tools.jackson.databind.JsonNode;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoOAuthClient.class);
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final KakaoOAuthProperties kakaoOAuthProperties;
    private final RestClient restClient = RestClient.create();

    public KakaoUserInfo fetchUserInfo(String authorizationCode) {
        String accessToken = exchangeToken(authorizationCode);
        return requestUserInfo(accessToken);
    }

    private String exchangeToken(String authorizationCode) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoOAuthProperties.getClientId());
        params.add("client_secret", kakaoOAuthProperties.getClientSecret());
        params.add("redirect_uri", kakaoOAuthProperties.getRedirectUri());
        params.add("code", authorizationCode);

        JsonNode response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("access_token")) {
            log.error("카카오 토큰 교환 실패");
            throw new YouthFitException(ErrorCode.UNAUTHORIZED, "카카오 인증에 실패했습니다");
        }

        return response.get("access_token").asText();
    }

    private KakaoUserInfo requestUserInfo(String accessToken) {
        JsonNode response = restClient.get()
                .uri(USER_INFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("id")) {
            log.error("카카오 사용자 정보 조회 실패");
            throw new YouthFitException(ErrorCode.UNAUTHORIZED, "카카오 사용자 정보를 가져올 수 없습니다");
        }

        String providerId = response.get("id").asText();
        JsonNode kakaoAccount = response.path("kakao_account");
        JsonNode profile = kakaoAccount.path("profile");

        return KakaoUserInfo.builder()
                .providerId(providerId)
                .email(kakaoAccount.path("email").asText(null))
                .nickname(profile.path("nickname").asText("사용자"))
                .profileImageUrl(profile.path("profile_image_url").asText(null))
                .build();
    }
}
