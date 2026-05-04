package com.youthfit.eligibility.application.dto.result;

import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.SourceView;
import com.youthfit.eligibility.domain.model.view.UserValueView;

public record CriterionResult(
        String field,
        String label,
        String result,
        UncertainReason uncertainReason,
        RequirementView requirement,
        UserValueView userValue,
        String verdictText,
        SourceView source
) {}
