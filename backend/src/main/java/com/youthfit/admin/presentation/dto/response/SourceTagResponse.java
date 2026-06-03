package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.SourceTagResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정책 출처 태그")
public record SourceTagResponse(
        @Schema(description = "출처 코드", example = "YOUTH_SEOUL_CRAWL") String code,
        @Schema(description = "출처 한글 표시명", example = "청년몽땅정보통") String label
) {
    public static SourceTagResponse from(SourceTagResult r) {
        return new SourceTagResponse(r.code(), r.label());
    }
}
