package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.PolicyProcessingListResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "정책 처리 현황 목록 응답 (페이징)")
public record PolicyProcessingListResponse(
        @Schema(description = "필터 적용 후 전체 건수") long totalCount,
        @Schema(description = "현재 페이지 (0-based)") int page,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "정책 처리 현황 행 목록") List<PolicyProcessingItemResponse> items
) {
    public static PolicyProcessingListResponse from(PolicyProcessingListResult r) {
        return new PolicyProcessingListResponse(
                r.totalCount(),
                r.page(),
                r.size(),
                r.items().stream().map(PolicyProcessingItemResponse::from).toList()
        );
    }
}
