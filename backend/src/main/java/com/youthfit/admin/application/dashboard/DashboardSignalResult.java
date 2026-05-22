package com.youthfit.admin.application.dashboard;

import com.youthfit.admin.presentation.dto.response.DashboardSeverity;

import java.time.Instant;

/**
 * 단일 대시보드 신호(이상 징후)의 평가 결과.
 *
 * <p>{@link DashboardSignal#evaluate(Instant)} 가 이상을 감지했을 때 반환한다.
 * 필드 순서는 다운스트림 컴포넌트(Builder, Comparator 등)가 의존하므로
 * 변경 시 함께 갱신해야 한다.</p>
 *
 * @param code        신호 식별 코드 (예: {@code ingestion.failure})
 * @param severity    심각도
 * @param title       사람이 읽는 짧은 제목
 * @param detail      상세 메시지 (수치·임계치 포함 가능)
 * @param deeplink    어드민 UI 내 상세 페이지로 이동하는 경로
 * @param detectedAt  신호가 감지된 시각
 */
public record DashboardSignalResult(
        String code,
        DashboardSeverity severity,
        String title,
        String detail,
        String deeplink,
        Instant detectedAt
) {
}
