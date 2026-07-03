package com.youthfit.eval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youthfit.eval")
public record EvalProperties(
        String datasetPath,
        String candidatePath,
        String cacheDir,
        String reportDir,
        Boolean runnerEnabled,
        Generate generate
) {
    /** 통합 테스트에서 EvalRunner 자동 실행을 끄는 스위치. 미설정(null)은 true 취급. */
    public boolean isRunnerEnabled() {
        return runnerEnabled == null || runnerEnabled;
    }

    public record Generate(String model, int maxTokens, String apiKey, int maxPerSource) {}
}
