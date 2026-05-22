package com.youthfit.admin.infrastructure.external;

import com.youthfit.admin.application.port.EnrichmentBacklogReader;
import com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult;
import com.youthfit.policy.application.service.AdminEnrichmentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Admin → policy 모듈 경계 어댑터. 신호는 이 포트만 보고,
 * policy 모듈 시그니처 변경은 이 클래스에 갇힌다.
 */
@Component
@RequiredArgsConstructor
public class EnrichmentBacklogAdapter implements EnrichmentBacklogReader {

    private final AdminEnrichmentQueryService queryService;

    @Override
    public long countNeedsReview() {
        EnrichmentReviewSummaryResult summary = queryService.findReviewSummary();
        return summary.needsReview();
    }
}
