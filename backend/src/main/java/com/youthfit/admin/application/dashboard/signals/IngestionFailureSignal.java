package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 최근 24시간 동안 {@code IngestionItemFailure} 가 1건 이상 발생하면 HIGH 신호를 발화한다.
 */
@Component
@RequiredArgsConstructor
public class IngestionFailureSignal implements DashboardSignal {

    private static final String CODE = "INGESTION_FAILURE";
    private static final Duration WINDOW = Duration.ofHours(24);
    private static final String DEEPLINK = "/admin/ingestion?tab=failures";

    private final IngestionItemFailureRepository repository;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        Instant since = now.minus(WINDOW);
        long count = repository.countCreatedAfter(since);
        if (count == 0L) {
            return Optional.empty();
        }
        return Optional.of(new DashboardSignalResult(
                CODE,
                DashboardSeverity.HIGH,
                "Ingestion 실패 " + count + "건 (최근 24시간)",
                null,
                DEEPLINK,
                now
        ));
    }
}
