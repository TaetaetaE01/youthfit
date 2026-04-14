package com.youthfit.guide.infrastructure.config;

import com.youthfit.guide.infrastructure.external.OpenAiChatProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenAiChatProperties.class)
public class GuideConfig {
}
