package com.youthfit.common.openai;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalBiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * yaml 으로 정의한 openai-chat / openai-embedding instance 에 RetryAfterIntervalFunction 을 주입한다.
 *
 * Why: yaml 만으로는 BiFunction<Integer, Throwable, Long> 동적 인터벌을 표현할 수 없다.
 * Resilience4j 의 RetryConfigCustomizer 로 instance 별 커스터마이즈.
 */
@Configuration
@RequiredArgsConstructor
public class OpenAiRetryConfig {

    private final RetryAfterIntervalFunction fn;

    @Bean
    @SuppressWarnings("unchecked")
    public RetryConfigCustomizer openAiChatRetryCustomizer() {
        return RetryConfigCustomizer.of("openai-chat", builder -> builder.intervalBiFunction(buildIntervalFn()));
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RetryConfigCustomizer openAiEmbeddingRetryCustomizer() {
        return RetryConfigCustomizer.of("openai-embedding", builder -> builder.intervalBiFunction(buildIntervalFn()));
    }

    private IntervalBiFunction<Object> buildIntervalFn() {
        return (attempt, either) -> {
            Throwable t = either.isLeft() ? either.getLeft() : null;
            return fn.apply(attempt, t);
        };
    }
}
