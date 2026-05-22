package com.youthfit.admin.application.port;

public interface EnrichmentBacklogReader {
    /** 검토가 필요한 enrichment 후보 개수. */
    long countNeedsReview();
}
