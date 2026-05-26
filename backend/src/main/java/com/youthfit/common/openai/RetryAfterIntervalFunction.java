package com.youthfit.common.openai;

import com.youthfit.common.exception.RetryableOpenAiException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

/**
 * Resilience4j RetryConfig 의 intervalBiFunction 으로 주입할 대기 시간 결정 함수.
 *
 * Why: OpenAI 429 응답에 Retry-After 헤더가 있으면 그 값을 우선 사용해
 * thundering herd 와 즉시 재요청을 방지한다. 헤더가 없거나 5xx/IO 인 경우
 * exponential backoff + jitter 로 fallback.
 *
 * Note: 단일 wait 는 30초로 clamp 한다.
 *
 * 주의: 이 클래스는 Resilience4j 의 IntervalBiFunction 시그니처와 직접 호환되지 않는다.
 * RetryConfigCustomizer 안에서 람다로 Either&lt;Throwable,?&gt;.getLeft() 를 풀어 호출하는 어댑터로 감싼다.
 */
@Component
public class RetryAfterIntervalFunction implements BiFunction<Integer, Throwable, Long> {

    private static final long BASE_MS = 2000L;
    private static final double MULTIPLIER = 2.0;
    private static final double JITTER = 0.5;
    private static final long MAX_WAIT_MS = 30_000L;

    @Override
    public Long apply(Integer attempt, Throwable lastException) {
        if (lastException instanceof RetryableOpenAiException ex) {
            Duration override = ex.getRetryAfter().orElse(null);
            if (override != null) {
                return clamp(override.toMillis());
            }
        }
        return clamp(exponentialWithJitter(attempt));
    }

    private long exponentialWithJitter(int attempt) {
        double base = BASE_MS * Math.pow(MULTIPLIER, attempt - 1.0);
        double jitterFactor = 1.0 - JITTER + ThreadLocalRandom.current().nextDouble() * 2 * JITTER;
        return (long) (base * jitterFactor);
    }

    private long clamp(long waitMs) {
        return Math.min(Math.max(waitMs, 0L), MAX_WAIT_MS);
    }
}
