package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Guide Entity")
class GuideTest {

    @Nested
    @DisplayName("hasChanged - 소스 변경 감지")
    class HasChanged {

        @Test
        @DisplayName("해시가 다르면 변경된 것으로 판단한다")
        void differentHash_returnsTrue() {
            // given
            Guide guide = createMockGuide("hash-v1");

            // when & then
            assertThat(guide.hasChanged("hash-v2")).isTrue();
        }

        @Test
        @DisplayName("해시가 같으면 변경되지 않은 것으로 판단한다")
        void sameHash_returnsFalse() {
            // given
            Guide guide = createMockGuide("hash-v1");

            // when & then
            assertThat(guide.hasChanged("hash-v1")).isFalse();
        }
    }

    @Test
    @DisplayName("가이드를 재생성하면 내용과 해시가 갱신된다")
    void regenerate_updatesContentAndHash() {
        // given
        Guide guide = createMockGuide("old-hash");

        // when
        guide.regenerate("<p>새로운 요약</p>", "new-hash");

        // then
        assertThat(guide.getSummaryHtml()).isEqualTo("<p>새로운 요약</p>");
        assertThat(guide.getSourceHash()).isEqualTo("new-hash");
    }

    // ── 헬퍼 메서드 ──

    private Guide createMockGuide(String sourceHash) {
        return Guide.builder()
                .policyId(1L)
                .summaryHtml("<p>기존 요약</p>")
                .sourceHash(sourceHash)
                .build();
    }
}
