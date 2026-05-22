package com.youthfit.admin.application.dto.result;

import java.time.Instant;
import java.util.List;

/**
 * 어드민 대시보드 오버뷰 결과(application).
 *
 * <p>{@code AdminDashboardOverviewService#findOverview()} 의 반환 타입.
 * presentation 레이어에서 {@code DashboardOverviewResponse} 로 매핑한다.</p>
 *
 * @param generatedAt   페이로드 생성 시각 (서버 평가 시각)
 * @param actionItems   발화된 액션 아이템
 * @param areas         6개 영역 상태
 */
public record DashboardOverviewResult(
        Instant generatedAt,
        List<DashboardActionItemResult> actionItems,
        List<DashboardAreaStatusResult> areas
) {
}
