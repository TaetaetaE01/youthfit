package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.UserProfileResult;
import com.youthfit.user.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserProfileResponse")
class UserProfileResponseTest {

    @Test
    @DisplayName("from(Result)는 Role enum을 문자열로 노출한다")
    void from_exposesRoleAsString() {
        UserProfileResult result = new UserProfileResult(
                1L, "a@x.com", "nick", null,
                Role.ADMIN,
                LocalDateTime.of(2026, 5, 5, 10, 0)
        );

        UserProfileResponse response = UserProfileResponse.from(result);

        assertThat(response.role()).isEqualTo("ADMIN");
    }
}
