package com.youthfit.qna.infrastructure.config;

import com.youthfit.qna.infrastructure.external.OpenAiQnaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OpenAiQnaProperties.class, QnaProperties.class, KeywordBoostProperties.class})
public class QnaConfig {
}
