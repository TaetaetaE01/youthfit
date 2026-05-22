package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QnaCacheHitDropSignalTest {

    private final QnaCacheLookupLogRepository repo = mock(QnaCacheLookupLogRepository.class);
    private final DashboardThresholds thresholds = thresholds("10");
    private final QnaCacheHitDropSignal signal = new QnaCacheHitDropSignal(repo, thresholds);

    @Test
    void code_is_QNA_CACHE_HIT_DROP() {
        assertThat(signal.code()).isEqualTo("QNA_CACHE_HIT_DROP");
    }

    @Test
    void empty_when_no_data_in_either_window() {
        // 두 윈도우 모두 total=0 → 비교 불가 → empty
        when(repo.hitTotalsBetween(any(), any()))
                .thenReturn(row(0L, 0L))   // recent
                .thenReturn(row(0L, 0L)); // prior

        assertThat(signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"))).isEmpty();
    }

    @Test
    void empty_when_drop_below_threshold() {
        // prior hit rate = 80/100 = 0.80, recent = 75/100 = 0.75
        // drop = 0.05 = 5pp < 10pp → empty
        when(repo.hitTotalsBetween(any(), any()))
                .thenReturn(row(75L, 100L))   // recent (called first)
                .thenReturn(row(80L, 100L)); // prior

        assertThat(signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"))).isEmpty();
    }

    @Test
    void medium_when_drop_meets_or_exceeds_threshold() {
        // prior = 80/100 = 0.80, recent = 60/100 = 0.60
        // drop = 0.20 = 20pp >= 10pp → MEDIUM
        when(repo.hitTotalsBetween(any(), any()))
                .thenReturn(row(60L, 100L))   // recent
                .thenReturn(row(80L, 100L)); // prior

        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        Optional<DashboardSignalResult> r = signal.evaluate(now);

        assertThat(r).isPresent();
        DashboardSignalResult result = r.get();
        assertThat(result.code()).isEqualTo("QNA_CACHE_HIT_DROP");
        assertThat(result.severity()).isEqualTo(DashboardSeverity.MEDIUM);
        assertThat(result.title()).contains("Q&A");
        assertThat(result.deeplink()).isEqualTo("/admin/qna-cache");
        assertThat(result.detectedAt()).isEqualTo(now);
    }

    private static List<Object[]> row(long hits, long total) {
        return Collections.singletonList(new Object[]{ hits, total });
    }

    private static DashboardThresholds thresholds(String hitDropPp) {
        return new DashboardThresholds(
                new DashboardThresholds.Llm(new BigDecimal("100000"), new BigDecimal("1.5")),
                new DashboardThresholds.Ingestion(7),
                new DashboardThresholds.Enrichment(20),
                new DashboardThresholds.Email(BigDecimal.ZERO, 0),
                new DashboardThresholds.QnaCache(new BigDecimal(hitDropPp)),
                new DashboardThresholds.PolicyIntake(BigDecimal.ZERO)
        );
    }
}
