package com.youthfit.common.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 호출에 공통 적용할 HTTP timeout 설정.
 *
 * Why: RestClient.create() 기본값은 timeout 무한. retry 와 결합 시 thread 영원히 block 위험.
 * default 는 connect 10s / read 60s — gpt-4o-mini 평균 1-5s, 긴 partial 호출 ~30-40s 기준.
 */
@ConfigurationProperties(prefix = "openai.http")
public record OpenAiHttpProperties(
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public OpenAiHttpProperties {
        if (connectTimeoutSeconds <= 0) connectTimeoutSeconds = 10;
        if (readTimeoutSeconds <= 0) readTimeoutSeconds = 60;
    }
}
