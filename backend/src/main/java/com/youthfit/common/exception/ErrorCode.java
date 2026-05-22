package com.youthfit.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    INVALID_INPUT(400, "YF-001", "입력값이 올바르지 않습니다"),

    // 401
    UNAUTHORIZED(401, "YF-002", "인증이 필요합니다"),

    // 403
    FORBIDDEN(403, "YF-003", "접근 권한이 없습니다"),

    // 404
    NOT_FOUND(404, "YF-004", "리소스를 찾을 수 없습니다"),

    // 409
    DUPLICATE(409, "YF-005", "이미 존재하는 리소스입니다"),
    ENRICHMENT_JOB_CONFLICT(409, "YF-006", "이미 진행 중인 enrichment 잡이 존재합니다"),

    // 429
    ENRICHMENT_JOB_RATE_LIMITED(429, "YF-007", "enrichment 잡 레이트 리밋을 초과했습니다"),
    RAG_PREVIEW_RATE_LIMITED(429, "YF-008", "RAG preview 레이트 리밋을 초과했습니다"),

    // 500
    INTERNAL_ERROR(500, "YF-500", "서버 내부 오류가 발생했습니다");

    private final int status;
    private final String code;
    private final String message;
}
