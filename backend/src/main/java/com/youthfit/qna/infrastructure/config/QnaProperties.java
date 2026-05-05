package com.youthfit.qna.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "youthfit.qna")
public record QnaProperties(
        long cacheTtlHours,
        double relevanceDistanceThreshold,
        double semanticDistanceThreshold,
        Cache cache
) {

    public QnaProperties {
        if (cacheTtlHours <= 0) cacheTtlHours = 24;
        if (relevanceDistanceThreshold <= 0) relevanceDistanceThreshold = 0.4;
        if (semanticDistanceThreshold <= 0) semanticDistanceThreshold = 0.15;
        if (cache == null) cache = new Cache(null, 0);
    }

    public record Cache(
            BigDecimal estimatedSavingsPerHitUsd,
            int retentionDays
    ) {
        public Cache {
            if (estimatedSavingsPerHitUsd == null) estimatedSavingsPerHitUsd = new BigDecimal("0.0015");
            if (retentionDays <= 0) retentionDays = 90;
        }
    }
}
