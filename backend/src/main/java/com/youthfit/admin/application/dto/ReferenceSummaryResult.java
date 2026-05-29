package com.youthfit.admin.application.dto;

public record ReferenceSummaryResult(long total, long succeeded) {
    public static ReferenceSummaryResult placeholder() {
        return new ReferenceSummaryResult(0, 0);
    }
}
