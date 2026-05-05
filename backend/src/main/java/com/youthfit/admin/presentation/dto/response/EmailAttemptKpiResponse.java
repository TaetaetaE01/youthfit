package com.youthfit.admin.presentation.dto.response;

public record EmailAttemptKpiResponse(
    Bucket today, Bucket thisWeek, double successRate
) {
    public record Bucket(long sent, long delivered, long bounced, long failed) { }
}
