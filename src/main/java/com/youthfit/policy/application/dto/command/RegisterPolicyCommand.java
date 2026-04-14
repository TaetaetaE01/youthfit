package com.youthfit.policy.application.dto.command;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.SourceType;

import java.time.LocalDate;

public record RegisterPolicyCommand(
        String title,
        String summary,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        SourceType sourceType,
        String externalId,
        String sourceUrl,
        String rawJson,
        String sourceHash
) {
}
