package com.youthfit.auth.infrastructure.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProvider")
class JwtProviderTest {

    private JwtProvider jwtProvider;

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";
    private static final long ACCESS_EXPIRATION = 1800000L;   // 30분
    private static final long REFRESH_EXPIRATION = 1209600000L; // 14일

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
        jwtProvider = new JwtProvider(properties);
    }

    @Nested
    @DisplayName("createAccessToken - 액세스 토큰 생성")
    class CreateAccessToken {

        @Test
        @DisplayName("유효한 액세스 토큰을 생성한다")
        void createsValidToken() {
            // when
            String token = jwtProvider.createAccessToken(1L, "user@test.com", "USER");

            // then
            assertThat(token).isNotNull().isNotBlank();
            assertThat(jwtProvider.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("생성된 토큰에서 사용자 정보를 추출할 수 있다")
        void extractsUserInfo() {
            // given
            String token = jwtProvider.createAccessToken(42L, "user@test.com", "USER");

            // when & then
            assertThat(jwtProvider.extractUserId(token)).isEqualTo(42L);
            assertThat(jwtProvider.extractEmail(token)).isEqualTo("user@test.com");
            assertThat(jwtProvider.extractRole(token)).isEqualTo("USER");
        }
    }

    @Nested
    @DisplayName("createRefreshToken - 리프레시 토큰 생성")
    class CreateRefreshToken {

        @Test
        @DisplayName("유효한 리프레시 토큰을 생성한다")
        void createsValidToken() {
            // when
            String token = jwtProvider.createRefreshToken(1L, "user@test.com", "USER");

            // then
            assertThat(token).isNotNull().isNotBlank();
            assertThat(jwtProvider.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("액세스 토큰과 리프레시 토큰은 서로 다르다")
        void accessAndRefreshAreDifferent() {
            // when
            String accessToken = jwtProvider.createAccessToken(1L, "user@test.com", "USER");
            String refreshToken = jwtProvider.createRefreshToken(1L, "user@test.com", "USER");

            // then
            assertThat(accessToken).isNotEqualTo(refreshToken);
        }
    }

    @Nested
    @DisplayName("isValid - 토큰 유효성 검증")
    class IsValid {

        @Test
        @DisplayName("정상 토큰은 유효하다")
        void validToken_returnsTrue() {
            // given
            String token = jwtProvider.createAccessToken(1L, "user@test.com", "USER");

            // when & then
            assertThat(jwtProvider.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("변조된 토큰은 유효하지 않다")
        void tamperedToken_returnsFalse() {
            // given
            String token = jwtProvider.createAccessToken(1L, "user@test.com", "USER");
            String tampered = token.substring(0, token.length() - 5) + "xxxxx";

            // when & then
            assertThat(jwtProvider.isValid(tampered)).isFalse();
        }

        @Test
        @DisplayName("빈 문자열은 유효하지 않다")
        void emptyToken_returnsFalse() {
            assertThat(jwtProvider.isValid("")).isFalse();
        }

        @Test
        @DisplayName("null은 유효하지 않다")
        void nullToken_returnsFalse() {
            assertThat(jwtProvider.isValid(null)).isFalse();
        }

        @Test
        @DisplayName("임의 문자열은 유효하지 않다")
        void randomString_returnsFalse() {
            assertThat(jwtProvider.isValid("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("다른 시크릿으로 서명된 토큰은 유효하지 않다")
        void differentSecret_returnsFalse() {
            // given
            JwtProperties otherProps = new JwtProperties(
                    "other-secret-key-must-be-at-least-32-bytes-long!!", ACCESS_EXPIRATION, REFRESH_EXPIRATION);
            JwtProvider otherProvider = new JwtProvider(otherProps);
            String token = otherProvider.createAccessToken(1L, "user@test.com", "USER");

            // when & then
            assertThat(jwtProvider.isValid(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("isExpired - 토큰 만료 검증")
    class IsExpired {

        @Test
        @DisplayName("유효한 토큰은 만료되지 않았다")
        void validToken_returnsNotExpired() {
            // given
            String token = jwtProvider.createAccessToken(1L, "user@test.com", "USER");

            // when & then
            assertThat(jwtProvider.isExpired(token)).isFalse();
        }

        @Test
        @DisplayName("만료된 토큰은 만료 상태를 반환한다")
        void expiredToken_returnsTrue() {
            // given - 만료 시간 0ms로 설정하여 즉시 만료
            JwtProperties expiredProps = new JwtProperties(SECRET, 0L, 0L);
            JwtProvider expiredProvider = new JwtProvider(expiredProps);
            String token = expiredProvider.createAccessToken(1L, "user@test.com", "USER");

            // when & then
            assertThat(expiredProvider.isExpired(token)).isTrue();
        }

        @Test
        @DisplayName("변조된 토큰은 만료가 아닌 false를 반환한다")
        void tamperedToken_returnsFalse() {
            assertThat(jwtProvider.isExpired("invalid.token.here")).isFalse();
        }
    }

    @Nested
    @DisplayName("extractUserId / extractEmail / extractRole - 클레임 추출")
    class ExtractClaims {

        @Test
        @DisplayName("만료된 토큰에서 클레임을 추출하면 예외가 발생한다")
        void expiredToken_throwsException() {
            // given
            JwtProperties expiredProps = new JwtProperties(SECRET, 0L, 0L);
            JwtProvider expiredProvider = new JwtProvider(expiredProps);
            String token = expiredProvider.createAccessToken(1L, "user@test.com", "USER");

            // when & then
            assertThatThrownBy(() -> expiredProvider.extractUserId(token))
                    .isInstanceOf(Exception.class);
        }
    }
}
