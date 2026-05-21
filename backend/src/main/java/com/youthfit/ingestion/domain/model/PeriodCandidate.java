package com.youthfit.ingestion.domain.model;

import com.youthfit.common.domain.PeriodSource;

import java.time.LocalDate;

public record PeriodCandidate(
        LocalDate start,
        LocalDate end,
        PeriodSource source,
        double confidence,
        String evidence
) {
    public boolean hasSameRange(PeriodCandidate other) {
        return java.util.Objects.equals(this.start, other.start)
                && java.util.Objects.equals(this.end, other.end);
    }
}
