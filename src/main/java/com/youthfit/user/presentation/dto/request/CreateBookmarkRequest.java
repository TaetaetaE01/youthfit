package com.youthfit.user.presentation.dto.request;

import com.youthfit.user.application.dto.command.CreateBookmarkCommand;
import jakarta.validation.constraints.NotNull;

public record CreateBookmarkRequest(
        @NotNull(message = "정책 ID는 필수입니다")
        Long policyId
) {

    public CreateBookmarkCommand toCommand() {
        return new CreateBookmarkCommand(policyId);
    }
}
