package com.youthfit.rag.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.hybrid")
public record HybridSearchProperties(
        boolean enabled,
        int topNPerSearch,
        int rrfK,
        double trigramThreshold
) {
    public HybridSearchProperties {
        if (topNPerSearch <= 0) topNPerSearch = 20;
        if (rrfK <= 0) rrfK = 60;
        if (trigramThreshold <= 0) trigramThreshold = 0.1;
    }
}
