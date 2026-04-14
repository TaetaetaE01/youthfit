package com.youthfit.eligibility.presentation.dto.response;

import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;

import java.util.List;

public record EligibilityJudgmentResponse(
        Long policyId,
        String policyTitle,
        String overallResult,
        List<CriterionResponse> criteria,
        List<String> missingFields,
        String disclaimer
) {

    public static EligibilityJudgmentResponse from(EligibilityJudgmentResult result) {
        List<CriterionResponse> criteriaResponses = result.criteria().stream()
                .map(CriterionResponse::from)
                .toList();

        return new EligibilityJudgmentResponse(
                result.policyId(),
                result.policyTitle(),
                result.overallResult().name(),
                criteriaResponses,
                result.missingFields(),
                result.disclaimer()
        );
    }
}
