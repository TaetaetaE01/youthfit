package com.youthfit.admin.presentation.dto.response;

import java.time.Instant;

/**
 * 어드민 대시보드 액션 아이템 응답 DTO.
 *
 * <p>발화된 {@code DashboardSignalResult} 1건을 프론트에 그대로 노출하기 위한 표현이다.
 * {@code severity} 는 {@code DashboardSeverity} 의 enum 이름 문자열(HIGH / MEDIUM)이다.
 * application enum 변경이 외부 API 계약에 새지 않도록 문자열로 직렬화한다.</p>
 *
 * @param code        신호 식별 코드
 * @param severity    심각도 enum 이름 (HIGH / MEDIUM)
 * @param title       사람이 읽는 짧은 제목
 * @param detail      상세 메시지
 * @param deeplink    어드민 UI 상세 페이지 경로
 * @param detectedAt  감지 시각
 */
public record DashboardActionItemResponse(
        String code,
        String severity,
        String title,
        String detail,
        String deeplink,
        Instant detectedAt
) {
}
