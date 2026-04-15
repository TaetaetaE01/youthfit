package com.youthfit.rag.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyDocument Entity")
class PolicyDocumentTest {

    @Test
    @DisplayName("Builder로 문서를 생성하면 모든 필드가 설정된다")
    void builder_setsAllFields() {
        // given & when
        PolicyDocument doc = PolicyDocument.builder()
                .policyId(1L)
                .chunkIndex(0)
                .content("청년 취업 지원 정책입니다.")
                .sourceHash("abc123")
                .build();

        // then
        assertThat(doc.getPolicyId()).isEqualTo(1L);
        assertThat(doc.getChunkIndex()).isZero();
        assertThat(doc.getContent()).isEqualTo("청년 취업 지원 정책입니다.");
        assertThat(doc.getSourceHash()).isEqualTo("abc123");
    }

    @Nested
    @DisplayName("임베딩 관리")
    class EmbeddingManagement {

        @Test
        @DisplayName("생성 직후 hasEmbedding은 false이다")
        void newDocument_hasNoEmbedding() {
            // given
            PolicyDocument doc = createDocument();

            // then
            assertThat(doc.hasEmbedding()).isFalse();
        }

        @Test
        @DisplayName("updateEmbedding 후 hasEmbedding은 true이다")
        void afterUpdate_hasEmbedding() {
            // given
            PolicyDocument doc = createDocument();
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};

            // when
            doc.updateEmbedding(embedding);

            // then
            assertThat(doc.hasEmbedding()).isTrue();
            assertThat(doc.getEmbedding()).isEqualTo(embedding);
        }

        @Test
        @DisplayName("빈 배열로 업데이트하면 hasEmbedding은 false이다")
        void emptyArray_hasNoEmbedding() {
            // given
            PolicyDocument doc = createDocument();

            // when
            doc.updateEmbedding(new float[]{});

            // then
            assertThat(doc.hasEmbedding()).isFalse();
        }
    }

    // ── 헬퍼 메서드 ──

    private PolicyDocument createDocument() {
        return PolicyDocument.builder()
                .policyId(1L)
                .chunkIndex(0)
                .content("테스트 콘텐츠")
                .sourceHash("hash123")
                .build();
    }
}
