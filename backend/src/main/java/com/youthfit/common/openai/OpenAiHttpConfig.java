package com.youthfit.common.openai;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 6개 OpenAI 클라이언트가 공유할 RestClient.Builder 빈.
 *
 * Why: RestClient.create() 기본 timeout 은 무한. Wave 5 retry 와 결합하면 thread 가
 * OpenAI 응답을 영원히 기다리는 동안 retry 가 다시 호출해 thread pool 고갈 위험.
 * 공유 빌더 + 단일 properties 로 6곳 일관 적용.
 */
@Configuration
@EnableConfigurationProperties(OpenAiHttpProperties.class)
@RequiredArgsConstructor
public class OpenAiHttpConfig {

    private final OpenAiHttpProperties properties;

    @Bean("openAiRestClientBuilder")
    @Scope("prototype")
    public RestClient.Builder openAiRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));
        return RestClient.builder().requestFactory(factory);
    }
}
