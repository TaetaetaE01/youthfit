package com.youthfit.eligibility.application.dto.result;

import java.util.List;

public record GroupedCriteria(
        List<CriterionResult> ineligible,
        List<CriterionResult> uncertain,
        List<CriterionResult> eligible
) {}
