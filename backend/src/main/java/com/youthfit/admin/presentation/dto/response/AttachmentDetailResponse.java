package com.youthfit.admin.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 첨부 1건의 상세 (펼침 영역 — 표 2: 첨부별).
 *
 * <p>TODO Task 7: {@code AttachmentDetailResult} record 가 정의되면
 * {@code public static AttachmentDetailResponse from(AttachmentDetailResult r)} 를 추가한다.</p>
 */
@Schema(description = "정책 첨부 상세 (펼침 영역)")
public record AttachmentDetailResponse(
        @Schema(description = "첨부 ID") Long attachmentId,
        @Schema(description = "파일명") String filename,
        @Schema(description = "본문 추출 상태 (예: EXTRACTED/FAILED/PENDING)")
        String extractionStatus,
        @Schema(description = "임베딩 적재 여부") boolean embedded
) { }
