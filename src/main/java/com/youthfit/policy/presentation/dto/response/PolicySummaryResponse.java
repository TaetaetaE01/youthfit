package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.PolicyStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PolicySummaryResponse {

    private Long id;
    private String title;
    private String summary;
    private Category category;
    private String regionCode;
    private LocalDate applyStart;
    private LocalDate applyEnd;
    private PolicyStatus status;
    private DetailLevel detailLevel;

    public static PolicySummaryResponse from(PolicySummaryResult result) {
        return PolicySummaryResponse.builder()
                .id(result.getId())
                .title(result.getTitle())
                .summary(result.getSummary())
                .category(result.getCategory())
                .regionCode(result.getRegionCode())
                .applyStart(result.getApplyStart())
                .applyEnd(result.getApplyEnd())
                .status(result.getStatus())
                .detailLevel(result.getDetailLevel())
                .build();
    }
}
