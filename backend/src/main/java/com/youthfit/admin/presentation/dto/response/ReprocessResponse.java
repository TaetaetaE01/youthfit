package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.ReprocessResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 재실행 endpoint 공통 응답.
 *
 * <p>5 개 재실행 endpoint (단계/첨부 1건/첨부 일괄/RAG/전체 재처리) 가 공유한다.</p>
 */
@Schema(description = "재실행 큐잉 응답 (공통)")
public record ReprocessResponse(
        @Schema(description = "큐잉 성공 여부 (이미 진행 중이면 false 또는 409)") boolean queued,
        @Schema(description = "큐잉된 PolicyProcessingStep ID 리스트") List<Long> stepIds,
        @Schema(description = "운영자 안내 메시지", example = "재처리 큐잉됨") String message
) {
    public static ReprocessResponse from(ReprocessResult r) {
        return new ReprocessResponse(
                r.queued(),
                r.stepIds(),
                r.message()
        );
    }
}
