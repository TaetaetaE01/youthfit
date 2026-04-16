package com.youthfit.user.presentation.dto.request;

import com.youthfit.user.application.dto.command.UpdateProfileCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "닉네임은 필수입니다")
        @Size(max = 50, message = "닉네임은 50자를 초과할 수 없습니다")
        String nickname,

        String profileImageUrl,

        @Email(message = "올바른 이메일 형식이 아닙니다")
        @Size(max = 255, message = "이메일은 255자를 초과할 수 없습니다")
        String email
) {
    public UpdateProfileCommand toCommand() {
        return new UpdateProfileCommand(nickname, profileImageUrl, email);
    }
}
