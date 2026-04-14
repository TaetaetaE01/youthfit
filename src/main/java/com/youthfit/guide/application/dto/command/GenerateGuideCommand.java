package com.youthfit.guide.application.dto.command;

public record GenerateGuideCommand(
        Long policyId,
        String policyTitle,
        String documentContent
) {
}
