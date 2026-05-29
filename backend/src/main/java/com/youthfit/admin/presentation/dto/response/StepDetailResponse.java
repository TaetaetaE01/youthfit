package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.StepDetailResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 정책 처리 단계 1건의 상세 (펼침 영역 — 표 1: 단계별).
 *
 * <p>도메인 enum 누수를 피하기 위해 step / status 는 String 으로 노출한다.
 * 각각 {@link com.youthfit.policy.domain.model.ProcessingStep#name()} /
 * {@link com.youthfit.policy.domain.model.ProcessingStatus#name()} 와 1:1 매핑된다.</p>
 */
@Schema(description = "정책 처리 단계 상세 (펼침 영역)")
public record StepDetailResponse(
        @Schema(description = "단계명 (INGESTION/ENRICHMENT/GUIDE/RULE/RAG_INDEXING)")
        String step,
        @Schema(description = "단계 상태 (IN_PROGRESS/SUCCESS/FAILED/SKIPPED)")
        String status,
        @Schema(description = "처리 소요 시간 (ms). 종료 전이면 null") Long durationMs,
        @Schema(description = "재시도 횟수 (1=최초)") int attempt,
        @Schema(description = "실패/스킵 사유 (없으면 null)") String reason,
        @Schema(description = "단계 시작 시각") LocalDateTime startedAt,
        @Schema(description = "단계 종료 시각 (진행 중이면 null)") LocalDateTime finishedAt
) {
    public static StepDetailResponse from(StepDetailResult r) {
        return new StepDetailResponse(
                r.step().name(),
                r.status().name(),
                r.durationMs(),
                r.attempt(),
                r.reason(),
                r.startedAt(),
                r.finishedAt()
        );
    }
}
