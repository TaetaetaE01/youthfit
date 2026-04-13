package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserProfileResult {

    private final Long id;
    private final String email;
    private final String nickname;
    private final String profileImageUrl;
    private final LocalDateTime createdAt;

    public static UserProfileResult from(User user) {
        return UserProfileResult.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
