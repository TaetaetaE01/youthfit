package com.youthfit.admin.application.dto;

public record PolicyProcessingStatsResult(
    long totalCount,
    long completeCount,
    long partialCount,
    long incompleteCount,
    long recent24hCount
) {}
