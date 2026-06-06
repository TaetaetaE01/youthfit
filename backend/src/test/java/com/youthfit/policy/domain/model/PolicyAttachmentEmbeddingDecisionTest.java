package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyAttachment 임베딩 판정")
class PolicyAttachmentEmbeddingDecisionTest {

    private PolicyAttachment extractedAttachment(String text) {
        PolicyAttachment a = PolicyAttachment.builder()
                .name("붙임1.hwp").url("http://x/a.hwp").mediaType("application/x-hwp")
                .build();
        a.markDownloading();
        a.markDownloaded("key", "hash");
        a.markExtracting();
        a.markExtracted(text);
        return a;
    }

    @Nested
    @DisplayName("decideEmbedding")
    class DecideEmbedding {

        @Test
        @DisplayName("포함 판정을 저장한다")
        void includes() {
            PolicyAttachment a = extractedAttachment("내용");
            a.decideEmbedding(true, "실질 정책내용 포함");
            assertThat(a.getEmbeddingIncluded()).isTrue();
            assertThat(a.getEmbeddingDecisionReason()).isEqualTo("실질 정책내용 포함");
        }

        @Test
        @DisplayName("제외 판정을 저장한다")
        void excludes() {
            PolicyAttachment a = extractedAttachment("내용");
            a.decideEmbedding(false, "단순 동의서 양식");
            assertThat(a.getEmbeddingIncluded()).isFalse();
            assertThat(a.getEmbeddingDecisionReason()).isEqualTo("단순 동의서 양식");
        }

        @Test
        @DisplayName("사유가 500자를 넘으면 잘라 저장한다")
        void truncatesReason() {
            PolicyAttachment a = extractedAttachment("내용");
            a.decideEmbedding(true, "가".repeat(600));
            assertThat(a.getEmbeddingDecisionReason()).hasSize(500);
        }
    }

    @Nested
    @DisplayName("판정 리셋")
    class Reset {

        @Test
        @DisplayName("재추출(markExtracted)되면 판정이 null 로 리셋된다")
        void resetsOnReextraction() {
            PolicyAttachment a = extractedAttachment("v1");
            a.decideEmbedding(false, "제외");

            a.markPendingReextraction();
            a.markDownloading();
            a.markDownloaded("key2", "hash2");
            a.markExtracting();
            a.markExtracted("v2");

            assertThat(a.getEmbeddingIncluded()).isNull();
            assertThat(a.getEmbeddingDecisionReason()).isNull();
        }
    }
}
