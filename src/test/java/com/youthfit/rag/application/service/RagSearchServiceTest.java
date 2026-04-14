package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RagSearchService")
@ExtendWith(MockitoExtension.class)
class RagSearchServiceTest {

    @InjectMocks
    private RagSearchService ragSearchService;

    @Mock
    private PolicyDocumentRepository policyDocumentRepository;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Nested
    @DisplayName("searchRelevantChunks - 관련 청크 검색")
    class SearchRelevantChunks {

        @Test
        @DisplayName("쿼리가 null이면 전체 청크를 반환한다")
        void nullQuery_returnsAllChunks() {
            // given
            SearchChunksCommand command = new SearchChunksCommand(1L, null);
            List<PolicyDocument> chunks = List.of(
                    createChunkWithId(1L, 1L, 0, "첫 번째 청크"),
                    createChunkWithId(2L, 1L, 1, "두 번째 청크")
            );
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(chunks);

            // when
            List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

            // then
            assertThat(result).hasSize(2);
            verify(embeddingProvider, never()).embed(any());
        }

        @Test
        @DisplayName("쿼리가 빈 문자열이면 전체 청크를 반환한다")
        void blankQuery_returnsAllChunks() {
            // given
            SearchChunksCommand command = new SearchChunksCommand(1L, "   ");
            List<PolicyDocument> chunks = List.of(
                    createChunkWithId(1L, 1L, 0, "청크 내용")
            );
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(chunks);

            // when
            List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

            // then
            assertThat(result).hasSize(1);
            verify(embeddingProvider, never()).embed(any());
        }

        @Test
        @DisplayName("벡터 검색 결과가 있으면 유사도 기반 결과를 반환한다")
        void vectorSearchHasResults_returnsSimilarChunks() {
            // given
            SearchChunksCommand command = new SearchChunksCommand(1L, "주거 지원");
            float[] queryEmbedding = new float[]{0.1f, 0.2f};
            List<PolicyDocument> similar = List.of(
                    createChunkWithId(1L, 1L, 0, "주거 지원 관련 청크")
            );

            given(embeddingProvider.embed("주거 지원")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(queryEmbedding), eq(5)))
                    .willReturn(similar);

            // when
            List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).content()).isEqualTo("주거 지원 관련 청크");
        }

        @Test
        @DisplayName("벡터 검색 결과가 없으면 키워드 폴백 검색을 수행한다")
        void vectorSearchEmpty_fallsBackToKeyword() {
            // given
            SearchChunksCommand command = new SearchChunksCommand(1L, "월세");
            float[] queryEmbedding = new float[]{0.1f};
            List<PolicyDocument> allChunks = List.of(
                    createChunkWithId(1L, 1L, 0, "청년 월세 지원 프로그램"),
                    createChunkWithId(2L, 1L, 1, "주거 안정 지원금")
            );

            given(embeddingProvider.embed("월세")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(queryEmbedding), eq(5)))
                    .willReturn(List.of());
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(allChunks);

            // when
            List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).content()).contains("월세");
        }

        @Test
        @DisplayName("키워드 폴백 검색은 대소문자를 무시한다")
        void keywordFallback_isCaseInsensitive() {
            // given
            SearchChunksCommand command = new SearchChunksCommand(1L, "HOUSING");
            float[] queryEmbedding = new float[]{0.1f};
            List<PolicyDocument> allChunks = List.of(
                    createChunkWithId(1L, 1L, 0, "Housing support program"),
                    createChunkWithId(2L, 1L, 1, "다른 내용")
            );

            given(embeddingProvider.embed("HOUSING")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(queryEmbedding), eq(5)))
                    .willReturn(List.of());
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(allChunks);

            // when
            List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).content()).contains("Housing");
        }

        @Test
        @DisplayName("키워드 폴백에서도 매칭이 없으면 빈 리스트를 반환한다")
        void keywordFallbackNoMatch_returnsEmpty() {
            // given
            SearchChunksCommand command = new SearchChunksCommand(1L, "존재하지않는키워드");
            float[] queryEmbedding = new float[]{0.1f};
            List<PolicyDocument> allChunks = List.of(
                    createChunkWithId(1L, 1L, 0, "청년 주거 지원")
            );

            given(embeddingProvider.embed("존재하지않는키워드")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(queryEmbedding), eq(5)))
                    .willReturn(List.of());
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(allChunks);

            // when
            List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ── 헬퍼 메서드 ──

    private PolicyDocument createChunkWithId(Long id, Long policyId, int index, String content) {
        PolicyDocument chunk = PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(index)
                .content(content)
                .sourceHash("test-hash")
                .build();
        ReflectionTestUtils.setField(chunk, "id", id);
        return chunk;
    }
}
