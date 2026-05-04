package com.youthfit.eligibility.presentation.dto.response;

import com.youthfit.eligibility.application.dto.result.GroupedCriteria;

import java.util.List;

public record GroupedCriteriaResponse(
        List<CriterionResponse> ineligible,
        List<CriterionResponse> uncertain,
        List<CriterionResponse> eligible
) {

    public static GroupedCriteriaResponse from(GroupedCriteria g) {
        return new GroupedCriteriaResponse(
                g.ineligible().stream().map(CriterionResponse::from).toList(),
                g.uncertain().stream().map(CriterionResponse::from).toList(),
                g.eligible().stream().map(CriterionResponse::from).toList()
        );
    }
}
