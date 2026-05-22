package com.youthfit.admin.application.dashboard;

import java.time.Instant;
import java.util.Optional;

/**
 * 어드민 대시보드의 단일 이상 징후 신호.
 *
 * <p>구현체는 특정 도메인(수집, 보강, LLM 비용 등)의 상태를 평가해
 * 이상이면 {@link DashboardSignalResult}, 정상이면 {@link Optional#empty()} 를 반환한다.</p>
 *
 * <p>{@link #evaluate(Instant)} 의 {@code now} 파라미터는 테스트 용이성을 위해
 * 외부에서 시각을 주입할 수 있도록 한다. 구현체 내부에서 {@link Instant#now()}
 * 를 직접 호출하지 않는다.</p>
 */
public interface DashboardSignal {

    /**
     * 신호 식별 코드. 응답 DTO 의 {@code code} 필드와 일치한다.
     */
    String code();

    /**
     * 현재 시각 {@code now} 기준으로 신호를 평가한다.
     *
     * @param now 평가 기준 시각 (테스트에서 주입 가능)
     * @return 이상 징후가 감지되면 결과, 정상이면 {@link Optional#empty()}
     */
    Optional<DashboardSignalResult> evaluate(Instant now);
}
