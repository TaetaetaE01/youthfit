package com.youthfit.rag.infrastructure.config;

import com.youthfit.rag.domain.service.DocumentChunker;
import com.youthfit.rag.infrastructure.external.OpenAiEmbeddingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenAiEmbeddingProperties.class)
public class RagConfig {

    @Bean
    public DocumentChunker documentChunker() {
        return new DocumentChunker();
    }
}
