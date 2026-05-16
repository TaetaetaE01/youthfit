package com.youthfit.qna.infrastructure.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAiQueryRewriter 입출력 변환")
class OpenAiQueryRewriterTest {

    @Nested
    @DisplayName("parseRewritten")
    class ParseRewritten {

        @Test
        @DisplayName("정상 응답은 trim 후 그대로 반환한다")
        void normal() {
            Optional<String> result = OpenAiQueryRewriter.parseRewritten(
                    "  청년내일저축계좌 최근 3개월 평균 근로사업소득 기준  "
            );
            assertThat(result).contains("청년내일저축계좌 최근 3개월 평균 근로사업소득 기준");
        }

        @Test
        @DisplayName("null 입력은 empty 반환")
        void nullInput() {
            assertThat(OpenAiQueryRewriter.parseRewritten(null)).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열은 empty 반환")
        void blank() {
            assertThat(OpenAiQueryRewriter.parseRewritten("   ")).isEmpty();
        }

        @Test
        @DisplayName("5자 미만은 empty 반환 (의미 있는 검색 query 가 아님)")
        void tooShort() {
            assertThat(OpenAiQueryRewriter.parseRewritten("질문")).isEmpty();
            assertThat(OpenAiQueryRewriter.parseRewritten("abc"))
                    .as("5자 미만이면 fallback 으로 분류")
                    .isEmpty();
        }

        @Test
        @DisplayName("200자 초과는 200자로 truncate 후 반환")
        void truncatesLong() {
            String longText = "가".repeat(250);
            Optional<String> result = OpenAiQueryRewriter.parseRewritten(longText);
            assertThat(result).isPresent();
            assertThat(result.get()).hasSize(200);
        }
    }

    @Nested
    @DisplayName("buildUserMessage")
    class BuildUserMessage {

        @Test
        @DisplayName("정책명·질문이 정해진 포맷으로 들어간다")
        void format() {
            String message = OpenAiQueryRewriter.buildUserMessage(
                    "청년내일저축계좌", "근로사업소득이 작년이 기준이야 올해가 기준이야?"
            );
            assertThat(message).contains("정책: 청년내일저축계좌");
            assertThat(message).contains("질문: 근로사업소득이 작년이 기준이야 올해가 기준이야?");
            assertThat(message).contains("재작성된 검색 query:");
        }
    }
}
