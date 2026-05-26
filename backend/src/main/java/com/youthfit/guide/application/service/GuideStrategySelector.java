package com.youthfit.guide.application.service;

import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.command.GuideGenerationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GuideGenerationInput 의 토큰 크기에 따라 단일 호출 vs map-reduce 분기.
 *
 * 임계값(default 80K) 은 gpt-4o-mini context window 128K 의 60% 여유.
 * system prompt(~2K) + meta(~3K) + 응답 max_tokens(~10K) 를 더해도 128K 이하 보장.
 */
@Component
public class GuideStrategySelector {

    private final TokenCounter tokenCounter;
    private final String model;
    private final int thresholdTokens;

    public GuideStrategySelector(
            TokenCounter tokenCounter,
            @Value("${openai.chat.model:gpt-4o-mini}") String model,
            @Value("${guide.map-reduce.threshold-tokens:80000}") int thresholdTokens) {
        this.tokenCounter = tokenCounter;
        this.model = model;
        this.thresholdTokens = thresholdTokens;
    }

    public GuideGenerationStrategy select(GuideGenerationInput input) {
        int tokens = tokenCounter.countTokens(input.combinedSourceText(), model);
        return tokens >= thresholdTokens
                ? GuideGenerationStrategy.MAP_REDUCE
                : GuideGenerationStrategy.SINGLE_CALL;
    }
}
