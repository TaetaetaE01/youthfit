package com.youthfit.ingestion.infrastructure.external;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "openai.ingestion.period")
public class OpenAiPolicyPeriodProperties {

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final int maxBodyChars;
}
