package com.youthfit.user.application.dto.command;

public record UpdateProfileCommand(
        String nickname,
        String profileImageUrl
) {
}
