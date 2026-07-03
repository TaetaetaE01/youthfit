package com.youthfit.qna.application.service;

import com.youthfit.qna.domain.model.QnaFallbackAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QnaContactFooter.appendIfPossible")
class QnaContactFooterTest {

    private static final String ANSWER = "신청 자격은 만 19~34세입니다.";
    private static final String FALLBACK_ANSWER = QnaFallbackAnswer.MESSAGE;

    @Nested
    @DisplayName("organization 과 contact 가 모두 있을 때")
    class BothPresent {

        @Test
        @DisplayName("일반 답변 끝에 구분선과 푸터가 첨부된다")
        void appendsFooter() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, "보건복지부", "129", false);

            assertThat(result).isEqualTo(ANSWER + "\n\n---\n\n📞 문의: 보건복지부 · 129");
        }

        @Test
        @DisplayName("fallback 답변일 때는 푸터가 첨부되지 않는다")
        void skipsForFallback() {
            String result = QnaContactFooter.appendIfPossible(FALLBACK_ANSWER, "보건복지부", "129", true);

            assertThat(result).isEqualTo(FALLBACK_ANSWER);
        }
    }

    @Nested
    @DisplayName("organization 또는 contact 한쪽이라도 비어있으면")
    class PartialMissing {

        @Test
        @DisplayName("organization 이 null 이면 푸터 미첨부")
        void organizationNull() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, null, "129", false);
            assertThat(result).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("contact 가 null 이면 푸터 미첨부")
        void contactNull() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, "보건복지부", null, false);
            assertThat(result).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("organization 이 빈 문자열이면 푸터 미첨부")
        void organizationBlank() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, "  ", "129", false);
            assertThat(result).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("contact 가 빈 문자열이면 푸터 미첨부")
        void contactBlank() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, "보건복지부", "", false);
            assertThat(result).isEqualTo(ANSWER);
        }
    }
}
