package com.youthfit.qna.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "youthfit.qna.keyword-boost")
public record KeywordBoostProperties(
        boolean enabled,
        int maxKeywords,
        List<String> stopwords
) {
    public KeywordBoostProperties {
        if (maxKeywords <= 0) maxKeywords = 5;
        if (stopwords == null) stopwords = List.of();
    }
}
