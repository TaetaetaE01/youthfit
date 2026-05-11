package com.youthfit.rag.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KeywordExtractor")
class KeywordExtractorTest {

    private final KeywordExtractor extractor =
            new KeywordExtractor(Set.of(), 5);

    @Nested
    @DisplayName("기본 토큰 추출")
    class BasicExtraction {

        @Test
        @DisplayName("한글 합성어를 한 토큰으로 추출한다")
        void koreanCompoundWord() {
            List<String> keywords = extractor.extract("디딤씨앗통장 중복 가능?");
            assertThat(keywords).containsExactly("디딤씨앗통장", "중복", "가능");
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("null query 는 빈 리스트를 반환한다")
        void nullQuery() {
            assertThat(extractor.extract(null)).isEmpty();
        }

        @Test
        @DisplayName("blank query 는 빈 리스트를 반환한다")
        void blankQuery() {
            assertThat(extractor.extract("   ")).isEmpty();
        }

        @Test
        @DisplayName("한영숫 혼용 토큰을 한 단위로 추출한다")
        void mixedAlphanumeric() {
            List<String> keywords = extractor.extract("30만원 GPT4 디딤");
            assertThat(keywords).containsExactly("30만원", "GPT4", "디딤");
        }

        @Test
        @DisplayName("1글자 토큰은 제외된다")
        void singleCharIgnored() {
            List<String> keywords = extractor.extract("나 너 우리 디딤");
            assertThat(keywords).containsExactly("우리", "디딤");
        }

        @Test
        @DisplayName("동일 토큰 중복은 1회만 유지한다")
        void deduplicate() {
            List<String> keywords = extractor.extract("청년 청년 청년 정책");
            assertThat(keywords).containsExactly("청년", "정책");
        }
    }

    @Nested
    @DisplayName("stopword 와 상한")
    class StopwordAndLimit {

        @Test
        @DisplayName("stopword 는 결과에서 제외된다")
        void stopwordExcluded() {
            KeywordExtractor withStopword = new KeywordExtractor(
                    Set.of("가능", "얼마"), 5);

            List<String> keywords = withStopword.extract("디딤씨앗통장 중복 가능 얼마");
            assertThat(keywords).containsExactly("디딤씨앗통장", "중복");
        }

        @Test
        @DisplayName("maxKeywords 상한이 적용된다")
        void maxKeywordsLimit() {
            KeywordExtractor limited = new KeywordExtractor(Set.of(), 2);

            List<String> keywords = limited.extract("청년 정책 지원 통장 신청");
            assertThat(keywords).hasSize(2).containsExactly("청년", "정책");
        }

        @Test
        @DisplayName("stopword 가 상한 카운트에 들어가지 않는다")
        void stopwordNotCounted() {
            KeywordExtractor limited = new KeywordExtractor(
                    Set.of("얼마", "어떻게"), 3);

            List<String> keywords = limited.extract("얼마 어떻게 청년 정책 지원 통장");
            assertThat(keywords).containsExactly("청년", "정책", "지원");
        }
    }
}
