package com.youthfit.qna.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QnaFallbackAnswer")
class QnaFallbackAnswerTest {

    @Test
    @DisplayName("MESSAGE 는 MARKER 를 포함한다 — 문구 변경 시 판정이 깨지지 않도록 상호 정합성을 지킨다")
    void messageContainsMarker() {
        assertThat(QnaFallbackAnswer.MESSAGE).contains(QnaFallbackAnswer.MARKER);
        assertThat(QnaFallbackAnswer.isFallback(QnaFallbackAnswer.MESSAGE)).isTrue();
    }

    @Test
    @DisplayName("LLM 이 문구를 변형해 출력해도 핵심 구절이 포함되면 fallback 으로 판정한다")
    void detectsVariantAnswer() {
        String variant = "죄송하지만 해당 내용은 정책 원문에 명시되어 있지 않아 답변드리기 어렵습니다.";

        assertThat(QnaFallbackAnswer.isFallback(variant)).isTrue();
    }

    @Test
    @DisplayName("일반 답변은 fallback 이 아니다")
    void normalAnswerIsNotFallback() {
        assertThat(QnaFallbackAnswer.isFallback("신청 자격은 만 19~34세입니다.")).isFalse();
    }

    @Test
    @DisplayName("null 답변은 fallback 이 아니다")
    void nullIsNotFallback() {
        assertThat(QnaFallbackAnswer.isFallback(null)).isFalse();
    }
}
