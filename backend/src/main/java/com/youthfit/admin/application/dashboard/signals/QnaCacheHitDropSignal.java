package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 최근 7일 Q&A 캐시 적중률이 직전 7일 적중률 대비
 * {@code thresholds.qnaCache.hitDropThresholdPp} (퍼센트 포인트) 이상 떨어지면
 * MEDIUM 신호를 발화한다.
 *
 * <p>비교 식: {@code prior_hit_rate - recent_hit_rate >= thresholdPp / 100}.
 * 두 윈도우 중 어느 한쪽이라도 표본(total) 이 0 이면 비교가 무의미하므로 무신호 처리한다.</p>
 */
@Component
@RequiredArgsConstructor
public class QnaCacheHitDropSignal implements DashboardSignal {

    private static final String CODE = "QNA_CACHE_HIT_DROP";
    private static final String DEEPLINK = "/admin/qna-cache";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final QnaCacheLookupLogRepository repository;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        LocalDate today = ZonedDateTime.ofInstant(now, KST).toLocalDate();
        LocalDateTime recentTo = today.atStartOfDay();
        LocalDateTime recentFrom = today.minusDays(7).atStartOfDay();
        LocalDateTime priorFrom = today.minusDays(14).atStartOfDay();

        HitTotal recent = readWindow(recentFrom, recentTo);
        HitTotal prior = readWindow(priorFrom, recentFrom);

        if (recent.total() == 0L || prior.total() == 0L) {
            return Optional.empty();
        }

        BigDecimal recentRate = rate(recent);
        BigDecimal priorRate = rate(prior);
        BigDecimal drop = priorRate.subtract(recentRate);

        BigDecimal thresholdRate = thresholds.getQnaCache().getHitDropThresholdPp()
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

        if (drop.compareTo(thresholdRate) < 0) {
            return Optional.empty();
        }

        BigDecimal dropPp = drop.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal recentPct = recentRate.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal priorPct = priorRate.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        String title = "Q&A 캐시 적중률 " + dropPp + "pp 하락 ("
                + priorPct + "% → " + recentPct + "%)";

        return Optional.of(new DashboardSignalResult(
                CODE,
                DashboardSeverity.MEDIUM,
                title,
                null,
                DEEPLINK,
                now
        ));
    }

    private HitTotal readWindow(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = repository.hitTotalsBetween(from, to);
        if (rows.isEmpty() || rows.get(0) == null) {
            return new HitTotal(0L, 0L);
        }
        Object[] row = rows.get(0);
        long hits = row[0] == null ? 0L : ((Number) row[0]).longValue();
        long total = row[1] == null ? 0L : ((Number) row[1]).longValue();
        return new HitTotal(hits, total);
    }

    private static BigDecimal rate(HitTotal w) {
        return BigDecimal.valueOf(w.hits())
                .divide(BigDecimal.valueOf(w.total()), 6, RoundingMode.HALF_UP);
    }

    private record HitTotal(long hits, long total) {
    }
}
