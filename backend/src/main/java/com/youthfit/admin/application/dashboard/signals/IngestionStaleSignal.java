package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 활성 ingestion source 중 마지막 수신 시각이 {@code thresholds.ingestion.staleDays} 일 이전인
 * source 가 있으면 HIGH 신호를 발화한다.
 */
@Component
@RequiredArgsConstructor
public class IngestionStaleSignal implements DashboardSignal {

    private static final String CODE = "INGESTION_STALE";
    private static final String DEEPLINK = "/admin/ingestion?filter=stale";

    private final IngestionRunLogRepository repository;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        int staleDays = thresholds.getIngestion().getStaleDays();
        Instant threshold = now.minus(Duration.ofDays(staleDays));
        List<Object[]> rows = repository.staleSources(threshold);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String detail = rows.stream()
                .map(r -> (String) r[0])
                .collect(Collectors.joining(", "));
        return Optional.of(new DashboardSignalResult(
                CODE,
                DashboardSeverity.HIGH,
                "출처 " + rows.size() + "개가 " + staleDays + "일 이상 미갱신",
                detail,
                DEEPLINK,
                now
        ));
    }
}
