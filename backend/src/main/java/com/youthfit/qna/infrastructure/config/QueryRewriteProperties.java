package com.youthfit.qna.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.query-rewrite")
public record QueryRewriteProperties(
        boolean enabled,
        String model,
        int maxTokens,
        double temperature,
        int timeoutMs
) {
    public QueryRewriteProperties {
        if (model == null || model.isBlank()) model = "gpt-4o-mini";
        if (maxTokens <= 0) maxTokens = 80;
        if (temperature < 0) temperature = 0.3;
        if (timeoutMs <= 0) timeoutMs = 5000;
    }
}
