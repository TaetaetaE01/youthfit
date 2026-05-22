package com.youthfit.admin.presentation.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 어드민 대시보드 오버뷰 응답 DTO.
 *
 * <p>{@code GET /api/v1/admin/dashboard/overview} 응답 페이로드.</p>
 *
 * @param generatedAt   페이로드 생성 시각 (서버 평가 시각)
 * @param actionItems   발화된 액션 아이템 (심각도 → 최신순 정렬)
 * @param areas         6개 영역 카드 (선언 순서 고정)
 */
public record DashboardOverviewResponse(
        Instant generatedAt,
        List<DashboardActionItemResponse> actionItems,
        List<DashboardAreaStatusResponse> areas
) {
}
