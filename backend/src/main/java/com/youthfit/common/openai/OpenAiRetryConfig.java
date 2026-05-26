package com.youthfit.common.openai;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * yaml 으로 정의한 openai-chat / openai-embedding instance 에 RetryAfterIntervalFunction 을 주입한다.
 *
 * Why: yaml 만으로는 BiFunction<Integer, Throwable, Long> 동적 인터벌을 표현할 수 없다.
 * Resilience4j 의 RetryConfigCustomizer 로 instance 별 커스터마이즈.
 */
@Configuration
public class OpenAiRetryConfig {

    @Bean
    public RetryConfigCustomizer openAiChatRetryCustomizer(RetryAfterIntervalFunction fn) {
        IntervalBiFunction<Object> intervalFn = buildIntervalFn(fn);
        return RetryConfigCustomizer.of("openai-chat", builder -> builder.intervalBiFunction(intervalFn));
    }

    @Bean
    public RetryConfigCustomizer openAiEmbeddingRetryCustomizer(RetryAfterIntervalFunction fn) {
        IntervalBiFunction<Object> intervalFn = buildIntervalFn(fn);
        return RetryConfigCustomizer.of("openai-embedding", builder -> builder.intervalBiFunction(intervalFn));
    }

    @SuppressWarnings("unchecked")
    private IntervalBiFunction<Object> buildIntervalFn(RetryAfterIntervalFunction fn) {
        return (attempt, eitherResultOrException) -> {
            Throwable t = ((Either<Throwable, Object>) eitherResultOrException).getLeft();
            return fn.apply(attempt, t);
        };
    }
}
