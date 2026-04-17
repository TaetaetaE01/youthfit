package com.youthfit.ingestion.domain.model;

import java.time.LocalDate;

public record PolicyPeriod(LocalDate start, LocalDate end) {

    private static final PolicyPeriod EMPTY = new PolicyPeriod(null, null);

    public static PolicyPeriod empty() {
        return EMPTY;
    }

    public static PolicyPeriod of(LocalDate start, LocalDate end) {
        return new PolicyPeriod(start, end);
    }

    public boolean isEmpty() {
        return start == null && end == null;
    }
}
