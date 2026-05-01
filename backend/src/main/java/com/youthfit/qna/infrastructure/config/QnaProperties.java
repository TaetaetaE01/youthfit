package com.youthfit.qna.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youthfit.qna")
public record QnaProperties(
        long cacheTtlHours,
        double relevanceDistanceThreshold,
        double semanticDistanceThreshold
) {

    public QnaProperties {
        if (cacheTtlHours <= 0) cacheTtlHours = 24;
        if (relevanceDistanceThreshold <= 0) relevanceDistanceThreshold = 0.4;
        if (semanticDistanceThreshold <= 0) semanticDistanceThreshold = 0.15;
    }
}
