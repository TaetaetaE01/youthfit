package com.youthfit.eval.dataset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SnippetMatcher")
class SnippetMatcherTest {

    @Test
    @DisplayName("개행·연속 공백이 달라도 스니펫 포함으로 판정한다")
    void matchesAcrossWhitespaceDifferences() {
        String chunk = "지원 대상은  만 19세~34세\n청년입니다.\n대학 재학생은   신청 대상에서 제외됩니다.";
        String snippet = "대학 재학생은 신청 대상에서\n제외됩니다.";

        assertThat(SnippetMatcher.containsSnippet(chunk, snippet)).isTrue();
    }

    @Test
    @DisplayName("비분리 공백·전각 공백이 섞여도 스니펫 포함으로 판정한다")
    void matchesAcrossUnicodeWhitespaceDifferences() {
        String chunk = "대학 재학생은 신청 대상에서　제외됩니다.";
        String snippet = "대학 재학생은 신청 대상에서 제외됩니다.";

        assertThat(SnippetMatcher.containsSnippet(chunk, snippet)).isTrue();
    }

    @Test
    @DisplayName("내용이 다르면 불일치")
    void rejectsDifferentContent() {
        assertThat(SnippetMatcher.containsSnippet("전세 보증금 지원", "월세 지원")).isFalse();
    }

    @Test
    @DisplayName("null·빈 입력은 불일치")
    void rejectsNullOrBlank() {
        assertThat(SnippetMatcher.containsSnippet(null, "스니펫")).isFalse();
        assertThat(SnippetMatcher.containsSnippet("본문", null)).isFalse();
        assertThat(SnippetMatcher.containsSnippet("본문", "  ")).isFalse();
    }
}
