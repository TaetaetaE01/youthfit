package com.youthfit.admin.application.dto.result;

import com.youthfit.admin.application.dashboard.AreaStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * 어드민 대시보드 영역 상태 결과(application).
 *
 * <p>status 는 {@link AreaStatus} enum 으로 보존하고, presentation 매핑 단계에서
 * 문자열로 직렬화한다.</p>
 *
 * @param key        영역 식별자
 * @param label      카드 헤더 라벨
 * @param status     상태 enum
 * @param summary    상태 요약 문구
 * @param sparkline  최근 추이 스파크라인 데이터
 * @param deeplink   어드민 UI 상세 페이지 경로
 */
public record DashboardAreaStatusResult(
        String key,
        String label,
        AreaStatus status,
        String summary,
        List<BigDecimal> sparkline,
        String deeplink
) {
}
