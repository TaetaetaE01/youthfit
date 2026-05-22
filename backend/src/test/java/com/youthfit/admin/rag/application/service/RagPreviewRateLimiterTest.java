package com.youthfit.admin.rag.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@DisplayName("RagPreviewRateLimiter")
@ExtendWith(MockitoExtension.class)
class RagPreviewRateLimiterTest {

    @InjectMocks private RagPreviewRateLimiter limiter;
    @Mock private StringRedisTemplate redis;

    @Test
    @DisplayName("첫 호출 시 INCR=1, Lua 스크립트로 EXPIRE 포함 실행, true 반환")
    void firstCall_atomicIncrExpire_andAllows() {
        given(redis.execute(any(RedisScript.class), anyList(), eq("60")))
                .willReturn(1L);

        assertThat(limiter.tryAcquire(42L)).isTrue();
    }

    @Test
    @DisplayName("count <= 30 이면 true")
    void underLimit_allows() {
        given(redis.execute(any(RedisScript.class), anyList(), eq("60")))
                .willReturn(30L);

        assertThat(limiter.tryAcquire(42L)).isTrue();
    }

    @Test
    @DisplayName("count > 30 이면 false")
    void overLimit_denies() {
        given(redis.execute(any(RedisScript.class), anyList(), eq("60")))
                .willReturn(31L);

        assertThat(limiter.tryAcquire(42L)).isFalse();
    }
}
