package com.youthfit.metrics.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class LlmModelPricingTest {

    @Test
    void gpt4oMini_은_promptTokens_과_completionTokens_을_각각_과금한다() {
        LlmModelPricing pricing = LlmModelPricing.GPT_4O_MINI;
        // 1000 prompt + 500 completion
        // input = 1000 * 0.000150 / 1000 = 0.000150
        // output = 500 * 0.000600 / 1000 = 0.000300
        // total = 0.000450
        BigDecimal cost = pricing.calculate(1000, 500);
        assertThat(cost).isEqualByComparingTo("0.000450");
    }

    @Test
    void embedding_모델은_completion_을_무시한다() {
        LlmModelPricing pricing = LlmModelPricing.TEXT_EMBEDDING_3_SMALL;
        // 10000 prompt * 0.00002 / 1000 = 0.000200
        BigDecimal cost = pricing.calculate(10000, 999); // completion 999는 무시
        assertThat(cost).isEqualByComparingTo("0.000200");
    }

    @Test
    void of_는_modelId_로_매칭하고_미등록은_UNKNOWN_을_반환한다() {
        assertThat(LlmModelPricing.of("gpt-4o-mini")).isEqualTo(LlmModelPricing.GPT_4O_MINI);
        assertThat(LlmModelPricing.of("nonexistent-model-x")).isEqualTo(LlmModelPricing.UNKNOWN);
    }

    @Test
    void UNKNOWN_은_언제나_0_을_반환한다() {
        assertThat(LlmModelPricing.UNKNOWN.calculate(99999, 99999)).isEqualByComparingTo("0");
    }

    @Test
    void zero_tokens_도_안전하게_0_을_반환한다() {
        assertThat(LlmModelPricing.GPT_4O_MINI.calculate(0, 0)).isEqualByComparingTo("0");
    }
}
