package com.youthfit.guide.infrastructure.external;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "openai.chat")
public class OpenAiChatProperties {

    private final String apiKey;
    private final String model;
    private final int maxTokens;
}
