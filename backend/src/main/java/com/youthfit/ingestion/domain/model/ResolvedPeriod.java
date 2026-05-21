package com.youthfit.ingestion.domain.model;

import com.youthfit.common.domain.PeriodSource;

import java.time.LocalDate;

public record ResolvedPeriod(
        LocalDate start,
        LocalDate end,
        PeriodSource source,
        double confidence,
        String evidence
) {
    private static final ResolvedPeriod EMPTY =
            new ResolvedPeriod(null, null, null, 0.0, null);

    public static ResolvedPeriod empty() { return EMPTY; }
    public boolean isEmpty() { return start == null && end == null; }

    public static ResolvedPeriod from(PeriodCandidate c) {
        return new ResolvedPeriod(c.start(), c.end(), c.source(), c.confidence(), c.evidence());
    }
}
