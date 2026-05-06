package com.youthfit.ingestion.application.dto.result;

public record RetryResult(
        Status status,
        String message,
        Long newFailureId
) {
    public enum Status { SUCCESS, FAILURE, NOT_FOUND, PAYLOAD_EXPIRED }

    public static RetryResult success() {
        return new RetryResult(Status.SUCCESS, "재처리 성공", null);
    }
    public static RetryResult failure(String msg, Long newFailureId) {
        return new RetryResult(Status.FAILURE, msg, newFailureId);
    }
    public static RetryResult notFound() {
        return new RetryResult(Status.NOT_FOUND, "실패 항목을 찾을 수 없습니다", null);
    }
    public static RetryResult payloadExpired() {
        return new RetryResult(Status.PAYLOAD_EXPIRED, "raw_payload 가 7일 경과로 만료되어 재처리할 수 없습니다", null);
    }
}
