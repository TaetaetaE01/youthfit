package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.User;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("UserProfileResult DTO 변환")
class UserProfileResultTest {

    @Test
    @DisplayName("User Entity로부터 프로필 필드를 올바르게 변환한다")
    void from_mapsAllFields() {
        // given
        User user = User.builder()
                .email("test@example.com")
                .nickname("테스트유저")
                .profileImageUrl("https://img.example.com/profile.jpg")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_123")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        // when
        UserProfileResult result = UserProfileResult.from(user);

        // then
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.id()).isEqualTo(1L);
            softly.assertThat(result.email()).isEqualTo("test@example.com");
            softly.assertThat(result.nickname()).isEqualTo("테스트유저");
            softly.assertThat(result.profileImageUrl()).isEqualTo("https://img.example.com/profile.jpg");
        });
    }
}
