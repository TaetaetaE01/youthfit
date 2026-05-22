package com.youthfit.admin.application.service;

import com.youthfit.admin.application.dashboard.AreaStatus;
import com.youthfit.admin.application.dashboard.AreaStatusBuilder;
import com.youthfit.admin.application.dashboard.AreaStatusBuilder.AreaKey;
import com.youthfit.admin.application.dashboard.DashboardSignalEvaluator;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardActionItemResponse;
import com.youthfit.admin.presentation.dto.response.DashboardAreaStatusResponse;
import com.youthfit.admin.presentation.dto.response.DashboardOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 어드민 대시보드 오버뷰 조회 유스케이스.
 *
 * <p>{@link DashboardSignalEvaluator} 를 호출해 발화된 신호 목록을 얻고,
 * {@link AreaStatusBuilder} 의 6개 영역 정의를 사용해 카드 상태를 계산한 뒤
 * 응답 DTO 로 변환한다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardOverviewService {

    private final DashboardSignalEvaluator evaluator;
    private final AreaStatusBuilder areaStatusBuilder;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse findOverview() {
        Instant now = Instant.now();
        List<DashboardSignalResult> fired = evaluator.evaluateAll(now);

        List<DashboardActionItemResponse> actionItems = new ArrayList<>(fired.size());
        for (DashboardSignalResult r : fired) {
            actionItems.add(new DashboardActionItemResponse(
                    r.code(),
                    r.severity(),
                    r.title(),
                    r.detail(),
                    r.deeplink(),
                    r.detectedAt()
            ));
        }

        List<AreaKey> areas = areaStatusBuilder.areas();
        List<DashboardAreaStatusResponse> areaResponses = new ArrayList<>(areas.size());
        for (AreaKey area : areas) {
            AreaStatus status = areaStatusBuilder.statusFor(area, fired);
            // sparkline 데이터는 v1.1 에서 채움
            areaResponses.add(new DashboardAreaStatusResponse(
                    area.key(),
                    area.label(),
                    status.name(),
                    summaryFor(status),
                    List.of(),
                    area.deeplink()
            ));
        }

        return new DashboardOverviewResponse(now, actionItems, areaResponses);
    }

    private static String summaryFor(AreaStatus status) {
        return switch (status) {
            case OK -> "정상";
            case WARN -> "주의";
            case CRITICAL -> "확인 필요";
        };
    }
}
