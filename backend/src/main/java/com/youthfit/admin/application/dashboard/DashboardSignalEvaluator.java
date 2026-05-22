package com.youthfit.admin.application.dashboard;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 모든 {@link DashboardSignal} 빈을 주입받아 평가하고 결과를 집계한다.
 *
 * <p>각 신호의 평가는 try/catch 로 격리되며, 한 신호가 실패해도 다른 신호 평가를
 * 중단하지 않는다. 실패는 WARN 레벨로 로깅되고 결과 목록에서 제외된다.</p>
 *
 * <p>최종 결과는 심각도 오름차순(HIGH 먼저, MEDIUM 다음) → 같은 심각도 내에서는
 * {@code detectedAt} 내림차순(최신 먼저) 으로 정렬된다.</p>
 */
@Component
@RequiredArgsConstructor
public class DashboardSignalEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DashboardSignalEvaluator.class);

    private final List<DashboardSignal> signals;

    /**
     * 모든 신호를 {@code now} 기준으로 평가하고 발화된 결과만 정렬해 반환한다.
     */
    public List<DashboardSignalResult> evaluateAll(Instant now) {
        List<DashboardSignalResult> results = new ArrayList<>();
        for (DashboardSignal s : signals) {
            try {
                Optional<DashboardSignalResult> r = s.evaluate(now);
                r.ifPresent(results::add);
            } catch (RuntimeException ex) {
                log.warn("Dashboard signal {} failed: {}", s.code(), ex.getMessage(), ex);
            }
        }
        results.sort(Comparator
                .comparing(DashboardSignalResult::severity)
                .thenComparing(DashboardSignalResult::detectedAt, Comparator.reverseOrder()));
        return results;
    }

    /**
     * 주입된 신호 빈 목록. 다운스트림(진단/디버깅) 용도로 노출한다.
     */
    public List<DashboardSignal> signals() {
        return signals;
    }
}
