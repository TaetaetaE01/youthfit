package com.youthfit.auth.application.service;

import com.youthfit.auth.application.dto.command.KakaoLoginCommand;
import com.youthfit.auth.application.dto.result.TokenResult;
import com.youthfit.auth.infrastructure.jwt.JwtProvider;
import com.youthfit.auth.infrastructure.oauth.KakaoOAuthClient;
import com.youthfit.auth.infrastructure.oauth.KakaoUserInfo;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final JwtProvider jwtProvider;

    @Transactional
    public TokenResult loginWithKakao(KakaoLoginCommand command) {
        KakaoUserInfo kakaoUserInfo = kakaoOAuthClient.fetchUserInfo(command.getAuthorizationCode());

        User user = userRepository.findByAuthProviderAndProviderId(
                        AuthProvider.KAKAO, kakaoUserInfo.getProviderId())
                .orElseGet(() -> registerNewUser(kakaoUserInfo));

        TokenResult tokenResult = issueTokens(user);
        user.updateRefreshToken(tokenResult.getRefreshToken());

        return tokenResult;
    }

    @Transactional
    public TokenResult refreshAccessToken(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken)) {
            throw new YouthFitException(ErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다");
        }

        Long userId = jwtProvider.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new YouthFitException(ErrorCode.UNAUTHORIZED, "리프레시 토큰이 일치하지 않습니다");
        }

        TokenResult tokenResult = issueTokens(user);
        user.updateRefreshToken(tokenResult.getRefreshToken());

        return tokenResult;
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        user.clearRefreshToken();
    }

    private User registerNewUser(KakaoUserInfo kakaoUserInfo) {
        User newUser = User.builder()
                .email(kakaoUserInfo.getEmail())
                .nickname(kakaoUserInfo.getNickname())
                .profileImageUrl(kakaoUserInfo.getProfileImageUrl())
                .authProvider(AuthProvider.KAKAO)
                .providerId(kakaoUserInfo.getProviderId())
                .build();
        return userRepository.save(newUser);
    }

    private TokenResult issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtProvider.createRefreshToken(
                user.getId(), user.getEmail(), user.getRole().name());

        return TokenResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
