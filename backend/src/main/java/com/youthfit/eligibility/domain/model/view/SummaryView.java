package com.youthfit.eligibility.domain.model.view;

public record SummaryView(
        String headline,
        int eligibleCount,
        int uncertainCount,
        int ineligibleCount
) {}
