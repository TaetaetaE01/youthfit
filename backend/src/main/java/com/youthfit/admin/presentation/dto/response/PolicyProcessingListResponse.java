package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.PolicyProcessingListResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "정책 처리 현황 목록 응답 (페이징). totalCount 는 검색·지역 필터만 적용된 모집단 크기이며, "
        + "filteredItemCount 는 in-memory 빠른필터까지 적용한 페이지 결과 수다. "
        + "computed 필터(RAG_FAILED 등)는 SQL 사전 거름이 불가능해 페이지 후 후처리되므로 같은 totalCount 라도 "
        + "페이지마다 filteredItemCount 가 0 ~ items.length 사이로 변동될 수 있다.")
public record PolicyProcessingListResponse(
        @Schema(description = "검색·지역 SQL 필터까지 적용된 모집단 크기") long totalCount,
        @Schema(description = "현재 페이지 (0-based)") int page,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "in-memory 빠른필터까지 적용된 페이지 결과 수 (items.length 와 동일)") long filteredItemCount,
        @Schema(description = "정책 처리 현황 행 목록") List<PolicyProcessingItemResponse> items
) {
    public static PolicyProcessingListResponse from(PolicyProcessingListResult r) {
        return new PolicyProcessingListResponse(
                r.totalCount(),
                r.page(),
                r.size(),
                r.filteredItemCount(),
                r.items().stream().map(PolicyProcessingItemResponse::from).toList()
        );
    }
}
