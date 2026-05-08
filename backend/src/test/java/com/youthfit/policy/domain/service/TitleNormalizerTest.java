package com.youthfit.policy.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleNormalizerTest {

    @Test
    @DisplayName("null·empty 입력은 빈 문자열을 반환한다")
    void emptyInput() {
        assertThat(TitleNormalizer.normalize(null)).isEmpty();
        assertThat(TitleNormalizer.normalize("")).isEmpty();
    }

    @Test
    @DisplayName("공백·특수문자를 제거하고 영어는 소문자화한다")
    void stripsWhitespaceAndPunctuation() {
        assertThat(TitleNormalizer.normalize("청년월세 지원사업")).isEqualTo("청년월세지원사업");
        assertThat(TitleNormalizer.normalize("청년 월세 지원사업")).isEqualTo("청년월세지원사업");
        assertThat(TitleNormalizer.normalize("청년월세-지원·사업")).isEqualTo("청년월세지원사업");
        assertThat(TitleNormalizer.normalize("YOUTH Rent")).isEqualTo("youthrent");
    }

    @Test
    @DisplayName("동일 의미·다른 표기를 같은 정규화 결과로 만든다")
    void equivalentTitles() {
        String a = TitleNormalizer.normalize("청년월세 지원사업");
        String b = TitleNormalizer.normalize("청년 월세지원사업");
        String c = TitleNormalizer.normalize("청년월세-지원사업");
        assertThat(a).isEqualTo(b).isEqualTo(c);
    }

    @Test
    @DisplayName("한글·영숫자 외 모든 글자를 제거한다")
    void onlyAllowsKoreanAndAlnum() {
        assertThat(TitleNormalizer.normalize("Test_Policy 2026!")).isEqualTo("testpolicy2026");
    }
}
