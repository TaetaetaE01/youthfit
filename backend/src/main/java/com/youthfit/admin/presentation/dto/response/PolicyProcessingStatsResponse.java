package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.PolicyProcessingStatsResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정책 처리 현황 KPI 응답 (대시보드 상단 카드 4개용)")
public record PolicyProcessingStatsResponse(
        @Schema(description = "정책 전체 건수") long totalCount,
        @Schema(description = "처리 완성도 COMPLETE 건수") long completeCount,
        @Schema(description = "처리 완성도 PARTIAL 건수") long partialCount,
        @Schema(description = "처리 완성도 INCOMPLETE 건수") long incompleteCount,
        @Schema(description = "최근 24 시간 내 갱신된 정책 건수") long recent24hCount
) {
    public static PolicyProcessingStatsResponse from(PolicyProcessingStatsResult r) {
        return new PolicyProcessingStatsResponse(
                r.totalCount(),
                r.completeCount(),
                r.partialCount(),
                r.incompleteCount(),
                r.recent24hCount()
        );
    }
}
