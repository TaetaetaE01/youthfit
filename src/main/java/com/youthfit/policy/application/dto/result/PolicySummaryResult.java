package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.*;
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
public class PolicySummaryResult {

    private Long id;
    private String title;
    private String summary;
    private Category category;
    private String regionCode;
    private LocalDate applyStart;
    private LocalDate applyEnd;
    private PolicyStatus status;
    private DetailLevel detailLevel;

    public static PolicySummaryResult from(Policy policy) {
        return PolicySummaryResult.builder()
                .id(policy.getId())
                .title(policy.getTitle())
                .summary(policy.getSummary())
                .category(policy.getCategory())
                .regionCode(policy.getRegionCode())
                .applyStart(policy.getApplyStart())
                .applyEnd(policy.getApplyEnd())
                .status(policy.getStatus())
                .detailLevel(policy.getDetailLevel())
                .build();
    }
}
