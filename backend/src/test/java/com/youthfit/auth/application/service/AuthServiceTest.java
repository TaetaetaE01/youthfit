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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DisplayName("AuthService")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private JwtProvider jwtProvider;

    @Nested
    @DisplayName("loginWithKakao - 카카오 로그인")
    class LoginWithKakao {

        @Test
        @DisplayName("기존 사용자가 로그인하면 토큰을 발급한다")
        void existingUser_returnsToken() {
            // given
            KakaoUserInfo kakaoInfo = createMockKakaoUserInfo();
            User existingUser = createMockUser();

            given(kakaoOAuthClient.fetchUserInfo("auth-code")).willReturn(kakaoInfo);
            given(userRepository.findByAuthProviderAndProviderId(AuthProvider.KAKAO, "kakao_123"))
                    .willReturn(Optional.of(existingUser));
            given(jwtProvider.createAccessToken(any(), any(), any())).willReturn("access-token");
            given(jwtProvider.createRefreshToken(any(), any(), any())).willReturn("refresh-token");

            // when
            TokenResult result = authService.loginWithKakao(new KakaoLoginCommand("auth-code"));

            // then
            assertThat(result.accessToken()).isEqualTo("access-token");
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("신규 사용자가 로그인하면 회원가입 후 토큰을 발급한다")
        void newUser_registersAndReturnsToken() {
            // given
            KakaoUserInfo kakaoInfo = createMockKakaoUserInfo();
            User savedUser = createMockUser();

            given(kakaoOAuthClient.fetchUserInfo("auth-code")).willReturn(kakaoInfo);
            given(userRepository.findByAuthProviderAndProviderId(AuthProvider.KAKAO, "kakao_123"))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(savedUser);
            given(jwtProvider.createAccessToken(any(), any(), any())).willReturn("access-token");
            given(jwtProvider.createRefreshToken(any(), any(), any())).willReturn("refresh-token");

            // when
            TokenResult result = authService.loginWithKakao(new KakaoLoginCommand("auth-code"));

            // then
            assertThat(result.accessToken()).isNotNull();
            then(userRepository).should().save(any(User.class));
        }
    }

    @Nested
    @DisplayName("refreshAccessToken - 토큰 갱신")
    class RefreshAccessToken {

        @Test
        @DisplayName("유효한 리프레시 토큰으로 새 토큰을 발급한다")
        void validToken_returnsNewToken() {
            // given
            User user = createMockUser();
            user.updateRefreshToken("valid-refresh");

            given(jwtProvider.isValid("valid-refresh")).willReturn(true);
            given(jwtProvider.extractUserId("valid-refresh")).willReturn(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtProvider.createAccessToken(any(), any(), any())).willReturn("new-access");
            given(jwtProvider.createRefreshToken(any(), any(), any())).willReturn("new-refresh");

            // when
            TokenResult result = authService.refreshAccessToken("valid-refresh");

            // then
            assertThat(result.accessToken()).isEqualTo("new-access");
            assertThat(result.refreshToken()).isEqualTo("new-refresh");
        }

        @Test
        @DisplayName("유효하지 않은 리프레시 토큰이면 UNAUTHORIZED 예외가 발생한다")
        void invalidToken_throwsUnauthorized() {
            // given
            given(jwtProvider.isValid("invalid-token")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken("invalid-token"))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    });
        }

        @Test
        @DisplayName("사용자를 찾을 수 없으면 NOT_FOUND 예외가 발생한다")
        void userNotFound_throwsNotFound() {
            // given
            given(jwtProvider.isValid("valid-token")).willReturn(true);
            given(jwtProvider.extractUserId("valid-token")).willReturn(999L);
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken("valid-token"))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("저장된 리프레시 토큰과 불일치하면 UNAUTHORIZED 예외가 발생한다")
        void tokenMismatch_throwsUnauthorized() {
            // given
            User user = createMockUser();
            user.updateRefreshToken("stored-token");

            given(jwtProvider.isValid("different-token")).willReturn(true);
            given(jwtProvider.extractUserId("different-token")).willReturn(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken("different-token"))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    });
        }
    }

    @Nested
    @DisplayName("logout - 로그아웃")
    class Logout {

        @Test
        @DisplayName("로그아웃하면 리프레시 토큰이 삭제된다")
        void validUser_clearsRefreshToken() {
            // given
            User user = createMockUser();
            user.updateRefreshToken("some-token");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // when
            authService.logout(1L);

            // then
            assertThat(user.getRefreshToken()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 사용자 ID로 로그아웃하면 NOT_FOUND 예외가 발생한다")
        void userNotFound_throwsNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.logout(999L))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    });
        }
    }

    // ── 헬퍼 메서드 ──

    private User createMockUser() {
        User user = User.builder()
                .email("test@example.com")
                .nickname("테스트유저")
                .profileImageUrl(null)
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_123")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private KakaoUserInfo createMockKakaoUserInfo() {
        return KakaoUserInfo.builder()
                .providerId("kakao_123")
                .email("test@example.com")
                .nickname("테스트유저")
                .profileImageUrl(null)
                .build();
    }
}
