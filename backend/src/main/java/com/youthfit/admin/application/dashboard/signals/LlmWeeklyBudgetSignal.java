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
 * 이번주(KST 월요일 00:00 ~ 현재) 누적 LLM 비용(KRW 환산)이
 * {@code weeklyBudgetKrw} 를 초과하면 HIGH 신호를 발화한다.
 */
@Component
public class LlmWeeklyBudgetSignal implements DashboardSignal {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LlmCostBucketRepository repo;
    private final DashboardThresholds thresholds;
    private final BigDecimal usdToKrw;

    public LlmWeeklyBudgetSignal(LlmCostBucketRepository repo,
                                 DashboardThresholds thresholds,
                                 @Value("${youthfit.metrics.llm-cost.usd-to-krw:1350}") BigDecimal usdToKrw) {
        this.repo = repo;
        this.thresholds = thresholds;
        this.usdToKrw = usdToKrw;
    }

    @Override
    public String code() {
        return "LLM_WEEKLY_BUDGET";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        ZonedDateTime nowKst = ZonedDateTime.ofInstant(now, KST);
        LocalDate today = nowKst.toLocalDate();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L); // 월요일
        Instant from = weekStart.atStartOfDay(KST).toInstant();

        List<Object[]> rows = repo.sumBetween(from, now);
        BigDecimal usd = (rows.isEmpty() || rows.get(0)[0] == null)
                ? BigDecimal.ZERO
                : (BigDecimal) rows.get(0)[0];
        BigDecimal krw = usd.multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP);

        BigDecimal budget = thresholds.getLlm().getWeeklyBudgetKrw();
        if (krw.compareTo(budget) <= 0) return Optional.empty();

        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "이번주 LLM 누적 ₩" + String.format("%,d", krw.longValueExact())
                        + " (예산 ₩" + String.format("%,d", budget.longValueExact()) + " 초과)",
                null,
                "/admin/llm-cost",
                now
        ));
    }
}
