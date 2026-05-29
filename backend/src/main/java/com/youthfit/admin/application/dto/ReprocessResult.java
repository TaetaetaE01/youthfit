package com.youthfit.admin.application.dto;

import java.util.List;

/**
 * 재실행 endpoint 공통 결과.
 *
 * <p>5종 재실행 endpoint (단계 / 첨부 1건 / 첨부 일괄 / RAG / 전체 재처리) 가 공유한다.
 * {@link #stepIds} 는 새로 큐잉된 {@code policy_processing_step} 행 id 들이다.
 * 이미 진행 중이라 큐잉이 거부된 경우 {@link #queued} 는 false 가 된다.
 */
public record ReprocessResult(
        boolean queued,
        List<Long> stepIds,
        String message
) {}
