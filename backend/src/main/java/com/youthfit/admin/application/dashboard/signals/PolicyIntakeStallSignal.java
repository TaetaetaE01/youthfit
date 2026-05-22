package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.application.port.PolicyIntakeStatsReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 오늘(KST) 신규로 적재된 정책 수가 직전 7일 일평균의
 * {@code thresholds.policyIntake.stallRatio} 배 미만이면 MEDIUM 신호를 발화한다.
 *
 * <p>직전 7일 합이 0이면 비교 기준이 없으므로 무신호로 처리한다.</p>
 */
@Component
@RequiredArgsConstructor
public class PolicyIntakeStallSignal implements DashboardSignal {

    private static final String CODE = "POLICY_INTAKE_STALL";
    private static final String DEEPLINK = "/admin/ingestion";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PolicyIntakeStatsReader repo;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        ZonedDateTime nowKst = ZonedDateTime.ofInstant(now, KST);
        LocalDate today = nowKst.toLocalDate();
        Instant todayStart = today.atStartOfDay(KST).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(KST).toInstant();
        Instant sevenDaysAgo = today.minusDays(7).atStartOfDay(KST).toInstant();

        long todayCount = repo.countCreatedBetween(todayStart, tomorrowStart);
        long priorWeek = repo.countCreatedBetween(sevenDaysAgo, todayStart);

        if (priorWeek == 0L) {
            return Optional.empty();
        }
        BigDecimal dailyAvg = BigDecimal.valueOf(priorWeek)
                .divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP);
        BigDecimal threshold = dailyAvg.multiply(thresholds.getPolicyIntake().getStallRatio());
        if (BigDecimal.valueOf(todayCount).compareTo(threshold) >= 0) {
            return Optional.empty();
        }

        return Optional.of(new DashboardSignalResult(
                CODE,
                DashboardSeverity.MEDIUM,
                "오늘 신규 정책 " + todayCount + "건 (7일 평균 "
                        + dailyAvg.setScale(1, RoundingMode.HALF_UP) + "건 대비 저조)",
                null,
                DEEPLINK,
                now
        ));
    }
}
