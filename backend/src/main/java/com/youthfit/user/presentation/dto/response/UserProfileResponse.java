package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.UserProfileResult;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(UserProfileResult result) {
        return new UserProfileResponse(
                result.id(),
                result.email(),
                result.nickname(),
                result.profileImageUrl(),
                result.createdAt()
        );
    }
}
