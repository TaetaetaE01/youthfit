package com.youthfit.admin.presentation.dto.response;

public record EmailAttemptDailyStatsResponse(
    String date,
    long sent, long delivered, long bounced, long complained, long failed
) { }
