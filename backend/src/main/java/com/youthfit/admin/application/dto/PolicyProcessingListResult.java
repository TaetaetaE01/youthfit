package com.youthfit.admin.application.dto;

import java.util.List;

public record PolicyProcessingListResult(
    long totalCount,
    int page,
    int size,
    List<PolicyProcessingItemResult> items
) {}
