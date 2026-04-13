package com.youthfit.policy.application.dto.result;

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
public class PolicyPageResult {

    private List<PolicySummaryResult> policies;
    private long totalCount;
    private int page;
    private int size;
    private int totalPages;
    private boolean hasNext;
}
