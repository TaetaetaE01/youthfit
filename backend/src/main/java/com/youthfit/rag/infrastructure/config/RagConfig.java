package com.youthfit.rag.infrastructure.config;

import com.youthfit.rag.domain.service.DocumentChunker;
import com.youthfit.rag.domain.service.KeywordExtractor;
import com.youthfit.rag.infrastructure.external.OpenAiEmbeddingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
@EnableConfigurationProperties({
        OpenAiEmbeddingProperties.class,
        KeywordBoostProperties.class,
        HybridSearchProperties.class
})
public class RagConfig {

    @Bean
    public DocumentChunker documentChunker() {
        return new DocumentChunker();
    }

    @Bean
    public KeywordExtractor keywordExtractor(KeywordBoostProperties properties) {
        Set<String> stopwords = properties.stopwords() == null
                ? Set.of()
                : new HashSet<>(properties.stopwords());
        return new KeywordExtractor(stopwords, properties.maxKeywords());
    }
}
