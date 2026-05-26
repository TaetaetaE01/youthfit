package com.youthfit.common.exception;

import java.time.Duration;
import java.util.Optional;

/**
 * OpenAI 호출의 transient 실패 (429 / 5xx / IOException) 를 마킹하는 예외.
 * Resilience4j 는 이 타입에만 retry 한다.
 *
 * Why: 4xx (non-429) 같은 영구 실패와 transient 실패를 분리해야 비용/시간 낭비 없이
 * 의미있는 retry 만 수행할 수 있다.
 */
public class RetryableOpenAiException extends RuntimeException {

    private final Duration retryAfter;

    public RetryableOpenAiException(String message, Throwable cause, Duration retryAfter) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
