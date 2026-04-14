package com.youthfit.rag.infrastructure.external;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "openai.embedding")
public class OpenAiEmbeddingProperties {

    private final String apiKey;
    private final String model;
    private final int dimensions;
}
