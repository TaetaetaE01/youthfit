package com.youthfit.admin.application.port;

import java.time.Instant;

public interface PolicyIntakeStatsReader {
    /** [from, to) 구간에 생성된 정책 건수. */
    long countCreatedBetween(Instant from, Instant to);
}
