package com.youthfit.eligibility.presentation.dto.response;

import com.youthfit.eligibility.application.dto.result.CriterionResult;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.SourceView;
import com.youthfit.eligibility.domain.model.view.UserValueView;

public record CriterionResponse(
        String field,
        String label,
        String result,
        String uncertainReason,
        RequirementView requirement,
        UserValueView userValue,
        String verdictText,
        SourceView source
) {

    public static CriterionResponse from(CriterionResult r) {
        return new CriterionResponse(
                r.field(),
                r.label(),
                r.result(),
                r.uncertainReason() == null ? null : r.uncertainReason().name(),
                r.requirement(),
                r.userValue(),
                r.verdictText(),
                r.source()
        );
    }
}
