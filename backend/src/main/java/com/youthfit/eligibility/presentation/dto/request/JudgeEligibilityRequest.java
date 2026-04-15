package com.youthfit.eligibility.presentation.dto.request;

import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import jakarta.validation.constraints.NotNull;

public record JudgeEligibilityRequest(
        @NotNull(message = "정책 ID는 필수입니다")
        Long policyId
) {

    public JudgeEligibilityCommand toCommand() {
        return new JudgeEligibilityCommand(policyId);
    }
}
