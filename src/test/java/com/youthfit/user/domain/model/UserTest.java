package com.youthfit.user.domain.model;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User Entity")
class UserTest {

    @Test
    @DisplayName("사용자 생성 시 기본 역할은 USER이다")
    void create_defaultRoleIsUser() {
        // given & when
        User user = createMockUser();

        // then
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Nested
    @DisplayName("updateProfile - 프로필 수정")
    class UpdateProfile {

        @Test
        @DisplayName("닉네임과 프로필 이미지를 수정한다")
        void validInput_updatesFields() {
            // given
            User user = createMockUser();

            // when
            user.updateProfile("새닉네임", "https://img.example.com/new.jpg");

            // then
            assertThat(user.getNickname()).isEqualTo("새닉네임");
            assertThat(user.getProfileImageUrl()).isEqualTo("https://img.example.com/new.jpg");
        }

        @Test
        @DisplayName("닉네임이 null이면 INVALID_INPUT 예외가 발생한다")
        void nullNickname_throwsException() {
            // given
            User user = createMockUser();

            // when & then
            assertThatThrownBy(() -> user.updateProfile(null, null))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    });
        }

        @Test
        @DisplayName("닉네임이 빈 문자열이면 INVALID_INPUT 예외가 발생한다")
        void blankNickname_throwsException() {
            // given
            User user = createMockUser();

            // when & then
            assertThatThrownBy(() -> user.updateProfile("   ", null))
                    .isInstanceOf(YouthFitException.class);
        }

        @Test
        @DisplayName("프로필 이미지 URL을 null로 설정할 수 있다")
        void nullProfileImage_isAllowed() {
            // given
            User user = createMockUser();

            // when
            user.updateProfile("닉네임", null);

            // then
            assertThat(user.getProfileImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("refreshToken 관리")
    class RefreshToken {

        @Test
        @DisplayName("리프레시 토큰을 업데이트한다")
        void updateRefreshToken_setsToken() {
            // given
            User user = createMockUser();

            // when
            user.updateRefreshToken("new-refresh-token");

            // then
            assertThat(user.getRefreshToken()).isEqualTo("new-refresh-token");
        }

        @Test
        @DisplayName("리프레시 토큰을 삭제한다")
        void clearRefreshToken_setsNull() {
            // given
            User user = createMockUser();
            user.updateRefreshToken("some-token");

            // when
            user.clearRefreshToken();

            // then
            assertThat(user.getRefreshToken()).isNull();
        }
    }

    @Test
    @DisplayName("적합도 판정용 프로필 정보를 업데이트한다")
    void updateEligibilityProfile_setsAllFields() {
        // given
        User user = createMockUser();

        // when
        user.updateEligibilityProfile(
                25, "서울", 30_000_000L,
                EmploymentStatus.UNEMPLOYED, EducationLevel.UNIVERSITY, 1);

        // then
        assertThat(user.getAge()).isEqualTo(25);
        assertThat(user.getRegion()).isEqualTo("서울");
        assertThat(user.getAnnualIncome()).isEqualTo(30_000_000L);
        assertThat(user.getEmploymentStatus()).isEqualTo(EmploymentStatus.UNEMPLOYED);
        assertThat(user.getEducationLevel()).isEqualTo(EducationLevel.UNIVERSITY);
        assertThat(user.getHouseholdSize()).isEqualTo(1);
    }

    // ── 헬퍼 메서드 ──

    private User createMockUser() {
        return User.builder()
                .email("test@example.com")
                .nickname("테스트유저")
                .profileImageUrl("https://img.example.com/profile.jpg")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_123")
                .build();
    }
}
