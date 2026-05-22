package com.youthfit.admin.rag.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window 60초 / admin user. 한도 초과 시 false 반환.
 * 키 형식: rate-limit:admin-rag-preview:{userId}
 */
@Component
@RequiredArgsConstructor
public class RagPreviewRateLimiter {

    static final int LIMIT_PER_MINUTE = 30;
    static final Duration WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public boolean tryAcquire(long userId) {
        String key = "rate-limit:admin-rag-preview:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
        return count != null && count <= LIMIT_PER_MINUTE;
    }
}
