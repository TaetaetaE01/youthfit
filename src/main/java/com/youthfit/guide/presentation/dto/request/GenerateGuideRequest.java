package com.youthfit.guide.presentation.dto.request;

import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateGuideRequest(
        @NotNull Long policyId,
        @NotBlank String policyTitle,
        @NotBlank String documentContent
) {

    public GenerateGuideCommand toCommand() {
        return new GenerateGuideCommand(policyId, policyTitle, documentContent);
    }
}
