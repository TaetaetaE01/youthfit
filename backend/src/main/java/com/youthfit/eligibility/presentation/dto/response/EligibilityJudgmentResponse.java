package com.youthfit.eligibility.presentation.dto.response;

import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.domain.model.view.SummaryView;

public record EligibilityJudgmentResponse(
        Long policyId,
        String policyTitle,
        String overallResult,
        SummaryView summary,
        GroupedCriteriaResponse criteria,
        String disclaimer
) {

    public static EligibilityJudgmentResponse from(EligibilityJudgmentResult result) {
        return new EligibilityJudgmentResponse(
                result.policyId(),
                result.policyTitle(),
                result.overallResult(),
                result.summary(),
                GroupedCriteriaResponse.from(result.criteria()),
                result.disclaimer()
        );
    }
}
