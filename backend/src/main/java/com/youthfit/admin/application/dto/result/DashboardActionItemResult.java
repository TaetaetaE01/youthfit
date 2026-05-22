package com.youthfit.admin.application.dto.result;

import com.youthfit.admin.application.dashboard.DashboardSeverity;

import java.time.Instant;

/**
 * 어드민 대시보드 액션 아이템 결과(application).
 *
 * <p>application 레이어가 controller 로 반환하는 출력 DTO. severity 는 도메인 enum
 * 그대로 보존하고, presentation 매핑 단계에서 문자열로 직렬화한다.</p>
 *
 * @param code        신호 식별 코드
 * @param severity    심각도 enum
 * @param title       사람이 읽는 짧은 제목
 * @param detail      상세 메시지
 * @param deeplink    어드민 UI 상세 페이지 경로
 * @param detectedAt  감지 시각
 */
public record DashboardActionItemResult(
        String code,
        DashboardSeverity severity,
        String title,
        String detail,
        String deeplink,
        Instant detectedAt
) {
}
