package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.User;

import java.time.LocalDateTime;

public record UserProfileResult(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        LocalDateTime createdAt
) {
    public static UserProfileResult from(User user) {
        return new UserProfileResult(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getCreatedAt()
        );
    }
}
