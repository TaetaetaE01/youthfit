package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicyPageResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PolicyPageResponse {

    private List<PolicySummaryResponse> content;
    private long totalCount;
    private int page;
    private int size;
    private int totalPages;
    private boolean hasNext;

    public static PolicyPageResponse from(PolicyPageResult result) {
        return PolicyPageResponse.builder()
                .content(result.getPolicies().stream().map(PolicySummaryResponse::from).toList())
                .totalCount(result.getTotalCount())
                .page(result.getPage())
                .size(result.getSize())
                .totalPages(result.getTotalPages())
                .hasNext(result.isHasNext())
                .build();
    }
}
