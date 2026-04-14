package com.youthfit.rag.infrastructure.config;

import com.youthfit.rag.domain.service.DocumentChunker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public DocumentChunker documentChunker() {
        return new DocumentChunker();
    }
}
