package com.youthfit.admin.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 참조 사이트 1건의 상세 (펼침 영역 — 표 3: 참조 사이트별).
 *
 * <p>Phase D 이전에는 비어 있는 리스트가 채워진다.</p>
 *
 * <p>TODO Task 7: {@code ReferenceDetailResult} record 가 정의되면
 * {@code public static ReferenceDetailResponse from(ReferenceDetailResult r)} 를 추가한다.</p>
 */
@Schema(description = "정책 참조 사이트 fetch 상세 (펼침 영역)")
public record ReferenceDetailResponse(
        @Schema(description = "참조 사이트 URL") String url,
        @Schema(description = "fetch 상태 (예: SUCCESS/FAILED/SKIPPED)")
        String status,
        @Schema(description = "본문에서 생성된 RAG 청크 개수") int chunkCount
) { }
