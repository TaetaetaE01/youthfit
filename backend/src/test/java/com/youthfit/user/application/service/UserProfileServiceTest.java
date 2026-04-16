package com.youthfit.user.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.user.application.dto.command.UpdateProfileCommand;
import com.youthfit.user.application.dto.result.UserProfileResult;
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
import static org.mockito.BDDMockito.given;

@DisplayName("UserProfileService")
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @InjectMocks
    private UserProfileService userProfileService;

    @Mock
    private UserRepository userRepository;

    @Nested
    @DisplayName("findMyProfile - 내 프로필 조회")
    class FindMyProfile {

        @Test
        @DisplayName("존재하는 사용자 ID로 프로필을 조회한다")
        void exists_returnsProfile() {
            // given
            User user = createMockUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // when
            UserProfileResult result = userProfileService.findMyProfile(1L);

            // then
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.email()).isEqualTo("test@example.com");
            assertThat(result.nickname()).isEqualTo("테스트유저");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 ID로 조회하면 NOT_FOUND 예외가 발생한다")
        void notExists_throwsNotFoundException() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userProfileService.findMyProfile(999L))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("updateMyProfile - 프로필 수정")
    class UpdateMyProfile {

        @Test
        @DisplayName("프로필을 수정하면 변경된 결과를 반환한다")
        void validCommand_updatesAndReturnsResult() {
            // given
            User user = createMockUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            UpdateProfileCommand command = new UpdateProfileCommand("새닉네임", "https://img.example.com/new.jpg", null);

            // when
            UserProfileResult result = userProfileService.updateMyProfile(1L, command);

            // then
            assertThat(result.nickname()).isEqualTo("새닉네임");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 ID로 수정하면 NOT_FOUND 예외가 발생한다")
        void userNotFound_throwsException() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());
            UpdateProfileCommand command = new UpdateProfileCommand("새닉네임", null, null);

            // when & then
            assertThatThrownBy(() -> userProfileService.updateMyProfile(999L, command))
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
                .profileImageUrl("https://img.example.com/profile.jpg")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_123")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
