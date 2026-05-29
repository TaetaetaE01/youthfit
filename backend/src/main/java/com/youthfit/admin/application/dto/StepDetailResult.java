package com.youthfit.admin.application.dto;

import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;

import java.time.LocalDateTime;

/**
 * 정책 처리 단계 1건의 펼침 영역 상세 결과.
 *
 * <p>{@code policy_processing_step} 의 step 별 최신 attempt 행을 그대로 노출한다.
 * 진행 중 단계는 {@link #durationMs}, {@link #finishedAt} 가 null 일 수 있다.
 */
public record StepDetailResult(
        ProcessingStep step,
        ProcessingStatus status,
        Long durationMs,
        int attempt,
        String reason,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {}
