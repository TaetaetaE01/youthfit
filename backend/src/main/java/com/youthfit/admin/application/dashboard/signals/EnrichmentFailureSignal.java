package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 최근 24시간 동안 {@code FAILED} 로 종료된 enrichment job 이 1건 이상 있으면 HIGH 신호를 발화한다.
 */
@Component
@RequiredArgsConstructor
public class EnrichmentFailureSignal implements DashboardSignal {

    private static final String CODE = "ENRICHMENT_FAILURE";
    private static final Duration WINDOW = Duration.ofHours(24);
    private static final String DEEPLINK = "/admin/enrichment?filter=failed";

    private final EnrichmentJobRepository repository;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        long failed = repository.countFailedSince(now.minus(WINDOW));
        if (failed == 0L) {
            return Optional.empty();
        }
        return Optional.of(new DashboardSignalResult(
                CODE,
                DashboardSeverity.HIGH,
                "Enrichment 실패 " + failed + "건 (최근 24시간)",
                null,
                DEEPLINK,
                now
        ));
    }
}
