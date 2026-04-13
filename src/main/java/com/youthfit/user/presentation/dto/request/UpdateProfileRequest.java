package com.youthfit.user.presentation.dto.request;

import com.youthfit.user.application.dto.command.UpdateProfileCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(max = 50, message = "닉네임은 50자를 초과할 수 없습니다")
    private String nickname;

    private String profileImageUrl;

    public UpdateProfileCommand toCommand() {
        return UpdateProfileCommand.builder()
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .build();
    }
}
