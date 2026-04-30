package com.youthfit.qna.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuestionNormalizer")
class QuestionNormalizerTest {

    private final QuestionNormalizer normalizer = new QuestionNormalizer();

    @Test
    @DisplayName("앞뒤 공백·중간 다중 공백 제거 + 소문자화")
    void normalizeText_collapsesAndLowercases() {
        String result = normalizer.normalize("  재학생도   가능한가요?  ");
        assertThat(result).isEqualTo("재학생도 가능한가요?");
    }

    @Test
    @DisplayName("같은 의미 다른 공백/대소문자는 같은 캐시 키로 변환된다")
    void cacheKey_sameForEquivalentInput() {
        String key1 = normalizer.cacheKey(10L, "  재학생도 가능한가요?");
        String key2 = normalizer.cacheKey(10L, "재학생도   가능한가요?");
        String key3 = normalizer.cacheKey(10L, "재학생도 가능한가요?");

        assertThat(key1).isEqualTo(key2).isEqualTo(key3);
        assertThat(key1).startsWith("qna:answer:10:");
    }

    @Test
    @DisplayName("정책 ID 가 다르면 캐시 키도 다르다")
    void cacheKey_differsByPolicyId() {
        String key1 = normalizer.cacheKey(10L, "동일 질문");
        String key2 = normalizer.cacheKey(20L, "동일 질문");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("질문이 다르면 캐시 키도 다르다")
    void cacheKey_differsByQuestion() {
        String key1 = normalizer.cacheKey(10L, "질문 A");
        String key2 = normalizer.cacheKey(10L, "질문 B");

        assertThat(key1).isNotEqualTo(key2);
    }
}
