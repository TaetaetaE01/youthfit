package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult;
import com.youthfit.policy.application.service.AdminEnrichmentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * 미리뷰 후보 누적 건수가 {@code thresholds.enrichment.backlogWarn} 이상이면 MEDIUM 신호를 발화한다.
 */
@Component
@RequiredArgsConstructor
public class EnrichmentBacklogSignal implements DashboardSignal {

    private static final String CODE = "ENRICHMENT_BACKLOG";
    private static final String DEEPLINK = "/admin/enrichment";

    private final AdminEnrichmentQueryService queryService;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        EnrichmentReviewSummaryResult summary = queryService.findReviewSummary();
        long count = summary.needsReview();
        int threshold = thresholds.getEnrichment().getBacklogWarn();
        if (count < threshold) {
            return Optional.empty();
        }
        return Optional.of(new DashboardSignalResult(
                CODE,
                DashboardSeverity.MEDIUM,
                "Enrichment 미리뷰 후보 " + count + "건 누적",
                null,
                DEEPLINK,
                now
        ));
    }
}
