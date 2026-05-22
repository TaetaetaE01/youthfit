package com.youthfit.admin.application.dashboard;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 발화된 {@link DashboardSignalResult} 목록을 6개 어드민 영역 카드의
 * OK/WARN/CRITICAL 상태로 매핑한다.
 *
 * <p>영역과 신호 코드의 매핑은 spec 3.2 의 영역 카드 정의를 따르며,
 * 영역 순서가 곧 응답 DTO 에서 카드가 노출되는 순서다.</p>
 */
@Component
public class AreaStatusBuilder {

    /**
     * 영역 메타데이터. {@code signalCodes} 가 영역에 속하는 신호 코드 집합이다.
     */
    public record AreaKey(String key, String label, String deeplink, List<String> signalCodes) {}

    private static final List<AreaKey> AREAS = List.of(
            new AreaKey("ingestion", "Ingestion", "/admin/ingestion",
                    List.of("INGESTION_FAILURE", "INGESTION_STALE")),
            new AreaKey("enrichment", "Enrichment", "/admin/enrichment",
                    List.of("ENRICHMENT_FAILURE", "ENRICHMENT_BACKLOG")),
            new AreaKey("llm-cost", "LLM 비용", "/admin/llm-cost",
                    List.of("LLM_COST_SPIKE", "LLM_WEEKLY_BUDGET")),
            new AreaKey("email", "Email", "/admin/email",
                    List.of("EMAIL_FAILURE")),
            new AreaKey("qna-cache", "Q&A Cache", "/admin/qna-cache",
                    List.of("QNA_CACHE_HIT_DROP")),
            new AreaKey("policy-intake", "신규 정책", "/admin/ingestion",
                    List.of("POLICY_INTAKE_STALL"))
    );

    /**
     * 6개 영역 정의를 선언 순서대로 반환한다.
     */
    public List<AreaKey> areas() {
        return AREAS;
    }

    /**
     * 발화된 신호 결과 목록을 기준으로 {@code area} 의 상태를 계산한다.
     *
     * <p>{@code area.signalCodes()} 에 포함되는 결과 중 가장 심각한 것이 상태를 결정한다.
     * {@link DashboardSeverity} 의 자연 순서(ordinal)가 HIGH &lt; MEDIUM 이므로
     * {@code compareTo} 비교로 더 작은 값이 더 심각하다.</p>
     */
    public AreaStatus statusFor(AreaKey area, List<DashboardSignalResult> firedResults) {
        DashboardSeverity worst = null;
        for (DashboardSignalResult r : firedResults) {
            if (area.signalCodes().contains(r.code())) {
                if (worst == null || r.severity().compareTo(worst) < 0) {
                    worst = r.severity();
                }
            }
        }
        if (worst == null) {
            return AreaStatus.OK;
        }
        return worst == DashboardSeverity.HIGH ? AreaStatus.CRITICAL : AreaStatus.WARN;
    }
}
