package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 어제 LLM 비용(USD)이 직전 7일 일평균의 {@code dailySpikeMultiplier} 배를 초과하면
 * HIGH 신호를 발화한다. 7일 합이 0이면 비교가 무의미하므로 건너뛴다.
 */
@Component
public class LlmCostSpikeSignal implements DashboardSignal {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LlmCostBucketRepository repo;
    private final DashboardThresholds thresholds;
    private final BigDecimal usdToKrw;

    public LlmCostSpikeSignal(LlmCostBucketRepository repo,
                              DashboardThresholds thresholds,
                              @Value("${youthfit.metrics.llm-cost.usd-to-krw:1350}") BigDecimal usdToKrw) {
        this.repo = repo;
        this.thresholds = thresholds;
        this.usdToKrw = usdToKrw;
    }

    @Override
    public String code() {
        return "LLM_COST_SPIKE";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        LocalDate today = ZonedDateTime.ofInstant(now, KST).toLocalDate();
        Instant yStart = today.minusDays(1).atStartOfDay(KST).toInstant();
        Instant tStart = today.atStartOfDay(KST).toInstant();
        Instant weekStart = today.minusDays(8).atStartOfDay(KST).toInstant();

        BigDecimal yesterdayUsd = sumUsd(yStart, tStart);
        BigDecimal lastSevenUsd = sumUsd(weekStart, yStart);

        // 7일 합이 0이면 평균 비교가 무의미하므로 무신호 처리 (div-by-zero 방어 포함)
        if (lastSevenUsd.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();

        BigDecimal avgUsd = lastSevenUsd.divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP);
        BigDecimal threshold = avgUsd.multiply(thresholds.getLlm().getDailySpikeMultiplier());
        if (yesterdayUsd.compareTo(threshold) <= 0) return Optional.empty();

        BigDecimal yesterdayKrw = yesterdayUsd.multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP);
        BigDecimal multiplier = yesterdayUsd.divide(avgUsd, 1, RoundingMode.HALF_UP);

        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "어제 LLM 비용 ₩" + format(yesterdayKrw) + " (7일 평균 " + multiplier + "배)",
                null,
                "/admin/llm-cost",
                now
        ));
    }

    private BigDecimal sumUsd(Instant from, Instant to) {
        List<Object[]> rows = repo.sumBetween(from, to);
        if (rows.isEmpty() || rows.get(0)[0] == null) return BigDecimal.ZERO;
        return (BigDecimal) rows.get(0)[0];
    }

    private static String format(BigDecimal v) {
        return String.format("%,d", v.longValueExact());
    }
}
