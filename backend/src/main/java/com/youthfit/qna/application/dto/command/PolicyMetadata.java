package com.youthfit.qna.application.dto.command;

import com.youthfit.policy.domain.model.Policy;

import java.time.LocalDate;

public record PolicyMetadata(
        String category,
        String summary,
        String supportTarget,
        String supportContent,
        String organization,
        String contact,
        LocalDate applyStart,
        LocalDate applyEnd,
        String provideType
) {

    public static PolicyMetadata from(Policy policy) {
        return new PolicyMetadata(
                policy.getCategory() == null ? null : policy.getCategory().name(),
                policy.getSummary(),
                policy.getSupportTarget(),
                policy.getSupportContent(),
                policy.getOrganization(),
                policy.getContact(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getProvideType()
        );
    }
}
