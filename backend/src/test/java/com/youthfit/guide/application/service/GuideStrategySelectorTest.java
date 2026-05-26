package com.youthfit.guide.application.service;

import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.ChunkInput;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.command.GuideGenerationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideStrategySelectorTest {

    private final TokenCounter tokenCounter = new TokenCounter();
    private final GuideStrategySelector selector =
            new GuideStrategySelector(tokenCounter, "gpt-4o-mini", 80_000);

    @Test
    @DisplayName("input tokens 가 임계값 미만이면 SINGLE_CALL")
    void belowThresholdSingleCall() {
        GuideGenerationInput input = buildInput("짧은 본문", List.of());
        assertThat(selector.select(input)).isEqualTo(GuideGenerationStrategy.SINGLE_CALL);
    }

    @Test
    @DisplayName("input tokens 가 임계값 이상이면 MAP_REDUCE")
    void atOrAboveThresholdMapReduce() {
        // 임계값 80K 를 확실히 넘기는 큰 청크 생성
        String big = "가".repeat(200_000);   // 한글 200K chars ≈ 100K+ tokens
        GuideGenerationInput input = buildInput("본문", List.of(
                new ChunkInput(big, null, null, null, "BODY")
        ));
        assertThat(selector.select(input)).isEqualTo(GuideGenerationStrategy.MAP_REDUCE);
    }

    private GuideGenerationInput buildInput(String body, List<ChunkInput> chunks) {
        return new GuideGenerationInput(
                1L, "title", 2025, "summary", body, null, null, null, null, null,
                null, chunks, null);
    }
}
