package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.PolicyStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PolicyDetailResponse {

    private Long id;
    private String title;
    private String summary;
    private Category category;
    private String regionCode;
    private LocalDate applyStart;
    private LocalDate applyEnd;
    private PolicyStatus status;
    private DetailLevel detailLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PolicyDetailResponse from(PolicyDetailResult result) {
        return PolicyDetailResponse.builder()
                .id(result.getId())
                .title(result.getTitle())
                .summary(result.getSummary())
                .category(result.getCategory())
                .regionCode(result.getRegionCode())
                .applyStart(result.getApplyStart())
                .applyEnd(result.getApplyEnd())
                .status(result.getStatus())
                .detailLevel(result.getDetailLevel())
                .createdAt(result.getCreatedAt())
                .updatedAt(result.getUpdatedAt())
                .build();
    }
}
