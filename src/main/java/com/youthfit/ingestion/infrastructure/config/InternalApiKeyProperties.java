package com.youthfit.ingestion.infrastructure.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "youthfit.internal")
public class InternalApiKeyProperties {

    private final String apiKey;
}
