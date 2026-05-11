package com.youthfit.qna.infrastructure.config;

import com.youthfit.qna.infrastructure.external.OpenAiQnaProperties;
import com.youthfit.rag.domain.service.KeywordExtractor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
@EnableConfigurationProperties({OpenAiQnaProperties.class, QnaProperties.class, KeywordBoostProperties.class})
public class QnaConfig {

    @Bean
    public KeywordExtractor keywordExtractor(KeywordBoostProperties properties) {
        Set<String> stopwords = properties.stopwords() == null
                ? Set.of()
                : new HashSet<>(properties.stopwords());
        return new KeywordExtractor(stopwords, properties.maxKeywords());
    }
}
