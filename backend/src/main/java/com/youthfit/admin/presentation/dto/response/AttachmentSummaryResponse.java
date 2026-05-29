package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.AttachmentSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정책 첨부 집계 요약 (목록 행)")
public record AttachmentSummaryResponse(
        @Schema(description = "정책에 연결된 첨부 총 개수") long total,
        @Schema(description = "본문 추출 완료된 첨부 개수") long extracted,
        @Schema(description = "임베딩까지 완료된 첨부 개수") long embedded
) {
    public static AttachmentSummaryResponse from(AttachmentSummaryResult r) {
        return new AttachmentSummaryResponse(r.total(), r.extracted(), r.embedded());
    }
}
