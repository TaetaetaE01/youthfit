package com.youthfit.admin.rag.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Fixed-window 60초 / admin user. 한도 초과 시 false 반환.
 * 키 형식: rate-limit:admin-rag-preview:{userId}
 * <p>
 * INCR + EXPIRE 를 단일 Lua 스크립트로 원자적으로 실행하여,
 * 두 명령 사이에 프로세스가 중단되어도 TTL 없는 키가 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RagPreviewRateLimiter {

    static final int LIMIT_PER_MINUTE = 30;
    static final Duration WINDOW = Duration.ofSeconds(60);

    private static final RedisScript<Long> INCR_EXPIRE_SCRIPT = RedisScript.of(
            "local n = redis.call('INCR', KEYS[1])\n" +
            "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return n", Long.class);

    private final StringRedisTemplate redis;

    public boolean tryAcquire(long userId) {
        String key = "rate-limit:admin-rag-preview:" + userId;
        Long count = redis.execute(INCR_EXPIRE_SCRIPT,
                List.of(key), String.valueOf(WINDOW.getSeconds()));
        return count != null && count <= LIMIT_PER_MINUTE;
    }
}
