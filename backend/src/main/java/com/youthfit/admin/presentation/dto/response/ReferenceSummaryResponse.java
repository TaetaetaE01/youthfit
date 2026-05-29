package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.ReferenceSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정책 참조 사이트 fetch 집계 요약 (목록 행). Phase D 이전에는 placeholder 0/0.")
public record ReferenceSummaryResponse(
        @Schema(description = "참조 사이트 총 개수") long total,
        @Schema(description = "fetch 성공한 참조 사이트 개수") long succeeded
) {
    public static ReferenceSummaryResponse from(ReferenceSummaryResult r) {
        return new ReferenceSummaryResponse(r.total(), r.succeeded());
    }
}
