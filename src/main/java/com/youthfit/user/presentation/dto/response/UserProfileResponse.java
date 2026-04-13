package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.UserProfileResult;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserProfileResponse {

    private final Long id;
    private final String email;
    private final String nickname;
    private final String profileImageUrl;
    private final LocalDateTime createdAt;

    public static UserProfileResponse from(UserProfileResult result) {
        return UserProfileResponse.builder()
                .id(result.getId())
                .email(result.getEmail())
                .nickname(result.getNickname())
                .profileImageUrl(result.getProfileImageUrl())
                .createdAt(result.getCreatedAt())
                .build();
    }
}
