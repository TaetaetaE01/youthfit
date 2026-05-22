package com.youthfit.admin.rag.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RagPreviewRateLimiter")
@ExtendWith(MockitoExtension.class)
class RagPreviewRateLimiterTest {

    @InjectMocks private RagPreviewRateLimiter limiter;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> ops;

    @Test
    @DisplayName("첫 호출 시 INCR=1 + EXPIRE 호출, true 반환")
    void firstCall_setsExpiry_andAllows() {
        given(redis.opsForValue()).willReturn(ops);
        given(ops.increment("rate-limit:admin-rag-preview:42")).willReturn(1L);

        assertThat(limiter.tryAcquire(42L)).isTrue();
        verify(redis).expire(eq("rate-limit:admin-rag-preview:42"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("count <= 30 이면 true, expire 재호출 없음")
    void underLimit_allowsWithoutExpire() {
        given(redis.opsForValue()).willReturn(ops);
        given(ops.increment(any())).willReturn(30L);

        assertThat(limiter.tryAcquire(42L)).isTrue();
        verify(redis, never()).expire(any(), any(Duration.class));
    }

    @Test
    @DisplayName("count > 30 이면 false")
    void overLimit_denies() {
        given(redis.opsForValue()).willReturn(ops);
        given(ops.increment(any())).willReturn(31L);

        assertThat(limiter.tryAcquire(42L)).isFalse();
    }
}
