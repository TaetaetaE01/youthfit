package com.youthfit.common.openai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiHttpPropertiesTest {

    @Test
    @DisplayName("양수 입력은 그대로 적용")
    void positiveValuesApply() {
        OpenAiHttpProperties p = new OpenAiHttpProperties(5, 30);

        assertThat(p.connectTimeoutSeconds()).isEqualTo(5);
        assertThat(p.readTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("0 이하 connect 는 default 10 으로 fallback")
    void zeroOrNegativeConnectFallsBack() {
        OpenAiHttpProperties zero = new OpenAiHttpProperties(0, 60);
        OpenAiHttpProperties negative = new OpenAiHttpProperties(-5, 60);

        assertThat(zero.connectTimeoutSeconds()).isEqualTo(10);
        assertThat(negative.connectTimeoutSeconds()).isEqualTo(10);
    }

    @Test
    @DisplayName("0 이하 read 는 default 60 으로 fallback")
    void zeroOrNegativeReadFallsBack() {
        OpenAiHttpProperties zero = new OpenAiHttpProperties(10, 0);
        OpenAiHttpProperties negative = new OpenAiHttpProperties(10, -1);

        assertThat(zero.readTimeoutSeconds()).isEqualTo(60);
        assertThat(negative.readTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("둘 다 0 이면 둘 다 default")
    void bothZeroFallBack() {
        OpenAiHttpProperties p = new OpenAiHttpProperties(0, 0);

        assertThat(p.connectTimeoutSeconds()).isEqualTo(10);
        assertThat(p.readTimeoutSeconds()).isEqualTo(60);
    }
}
