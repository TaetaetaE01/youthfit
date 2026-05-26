package com.youthfit.common.openai;

import com.youthfit.common.exception.RetryableOpenAiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterIntervalFunctionTest {

    private final RetryAfterIntervalFunction fn = new RetryAfterIntervalFunction();

    @Test
    @DisplayName("RetryableOpenAiException with retryAfter=5s → 5000ms 그대로")
    void retryAfterHeaderTakesPrecedence() {
        RetryableOpenAiException ex = new RetryableOpenAiException(
                "429", new RuntimeException(), Duration.ofSeconds(5));

        long waitMs = fn.apply(1, ex);

        assertThat(waitMs).isEqualTo(5000L);
    }

    @Test
    @DisplayName("RetryableOpenAiException with retryAfter=null → exponential fallback (attempt 1: 1000-3000ms)")
    void fallbackExponentialAttempt1() {
        RetryableOpenAiException ex = new RetryableOpenAiException(
                "5xx", new RuntimeException(), null);

        for (int i = 0; i < 20; i++) {
            long waitMs = fn.apply(1, ex);
            assertThat(waitMs).isBetween(1000L, 3000L);
        }
    }

    @Test
    @DisplayName("attempt 2 fallback → 2000-6000ms (2배 backoff)")
    void fallbackExponentialAttempt2() {
        RetryableOpenAiException ex = new RetryableOpenAiException(
                "5xx", new RuntimeException(), null);

        for (int i = 0; i < 20; i++) {
            long waitMs = fn.apply(2, ex);
            assertThat(waitMs).isBetween(2000L, 6000L);
        }
    }

    @Test
    @DisplayName("retryAfter=60s 는 30s 로 clamp")
    void clampsToThirtySeconds() {
        RetryableOpenAiException ex = new RetryableOpenAiException(
                "429", new RuntimeException(), Duration.ofSeconds(60));

        long waitMs = fn.apply(1, ex);

        assertThat(waitMs).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("비-RetryableOpenAiException → exponential fallback")
    void unknownExceptionUsesFallback() {
        RuntimeException ex = new RuntimeException("unknown");

        long waitMs = fn.apply(1, ex);

        assertThat(waitMs).isBetween(1000L, 3000L);
    }
}
