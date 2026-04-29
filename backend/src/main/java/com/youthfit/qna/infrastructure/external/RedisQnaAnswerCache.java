package com.youthfit.qna.infrastructure.external;

import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.port.QnaAnswerCache;
import com.youthfit.qna.application.service.QuestionNormalizer;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisQnaAnswerCache implements QnaAnswerCache {

    private static final Logger log = LoggerFactory.getLogger(RedisQnaAnswerCache.class);

    private final StringRedisTemplate redisTemplate;
    private final QuestionNormalizer normalizer;
    private final ObjectMapper objectMapper;
    private final QnaProperties properties;

    @Override
    public Optional<CachedAnswer> get(Long policyId, String question) {
        String key = normalizer.cacheKey(policyId, question);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, CachedAnswer.class));
        } catch (JacksonException | RuntimeException e) {
            log.warn("Q&A 캐시 read 실패 (정상 흐름 진행): policyId={}, error={}", policyId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long policyId, String question, CachedAnswer value) {
        String key = normalizer.cacheKey(policyId, question);
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(properties.cacheTtlHours()));
        } catch (JacksonException | RuntimeException e) {
            log.warn("Q&A 캐시 write 실패 (사용자 응답엔 영향 없음): policyId={}, error={}", policyId, e.toString());
        }
    }
}
