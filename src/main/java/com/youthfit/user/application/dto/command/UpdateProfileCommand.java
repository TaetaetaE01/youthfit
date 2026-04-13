package com.youthfit.user.application.dto.command;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateProfileCommand {

    private final String nickname;
    private final String profileImageUrl;
}
