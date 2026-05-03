package com.youthfit.eligibility.infrastructure.external;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "openai.eligibility-rule")
public class OpenAiEligibilityRuleProperties {

    private final String apiKey;
    private final String model;
    private final int maxTokens;
}
