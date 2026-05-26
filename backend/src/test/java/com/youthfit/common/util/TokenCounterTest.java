package com.youthfit.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCounterTest {

    private final TokenCounter counter = new TokenCounter();

    @Test
    @DisplayName("빈 문자열은 0 토큰")
    void emptyStringZeroTokens() {
        assertThat(counter.countTokens("", "gpt-4o-mini")).isZero();
    }

    @Test
    @DisplayName("gpt-4o-mini 는 o200k_base encoder 로 짧은 영어 텍스트를 토큰화한다")
    void shortEnglishTextHasExpectedTokens() {
        int tokens = counter.countTokens("hello world", "gpt-4o-mini");
        // jtokkit 버전에 따라 미세하게 다를 수 있으므로 범위로 확인
        assertThat(tokens).isBetween(1, 4);
    }

    @Test
    @DisplayName("한글 텍스트는 영어보다 토큰 밀도가 높다 (실제 사용 패턴 확인)")
    void koreanTextHasMoreTokensThanEnglish() {
        int en = counter.countTokens("Hello world this is a test sentence.", "gpt-4o-mini");
        int ko = counter.countTokens("안녕하세요 이것은 테스트 문장입니다.", "gpt-4o-mini");
        assertThat(ko).isGreaterThan(en);
    }

    @Test
    @DisplayName("text-embedding-3-small 도 cl100k_base 로 토큰화 가능")
    void embeddingModelTokenization() {
        int tokens = counter.countTokens("test text", "text-embedding-3-small");
        assertThat(tokens).isGreaterThan(0);
    }

    @Test
    @DisplayName("알 수 없는 모델은 o200k_base 로 fallback 하여 정상 카운트")
    void unknownModelFallsBackToO200k() {
        int tokens = counter.countTokens("hello world", "completely-unknown-model");
        assertThat(tokens).isGreaterThan(0);
    }

    @Test
    @DisplayName("dated suffix 가 붙은 gpt-4o-mini 도 정상 처리")
    void datedModelSuffixIsHandled() {
        int withSuffix = counter.countTokens("hello world", "gpt-4o-mini-2024-07-18");
        int withoutSuffix = counter.countTokens("hello world", "gpt-4o-mini");
        assertThat(withSuffix).isEqualTo(withoutSuffix);
    }
}
