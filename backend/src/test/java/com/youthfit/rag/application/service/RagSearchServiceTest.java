package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.PolicyDocumentSource;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import com.youthfit.rag.domain.service.KeywordExtractor;
import com.youthfit.rag.domain.service.ReciprocalRankFusion;
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
import com.youthfit.rag.infrastructure.config.KeywordBoostProperties;
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
import static org.mockito.Mockito.lenient;
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

    @Mock
    private KeywordExtractor keywordExtractor;

    @Mock
    private KeywordBoostProperties keywordBoostProperties;

    @Mock
    private HybridSearchProperties hybridSearchProperties;

    @Mock
    private ReciprocalRankFusion reciprocalRankFusion;

    @Mock
    private EffectiveConfigFactory effectiveConfigFactory;

    @Mock
    private TrigramSearchExecutor trigramSearchExecutor;

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
            List<SimilarChunk> similar = List.of(
                    createSimilarChunk(1L, 1L, 0, "주거 지원 관련 청크", 0.1)
            );

            given(keywordBoostProperties.enabled()).willReturn(true);
            given(keywordExtractor.extract("주거 지원")).willReturn(List.of("주거", "지원"));
            given(embeddingProvider.embed("주거 지원")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(
                    eq(1L), eq(queryEmbedding), eq(List.of("주거", "지원")), eq(10)))
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

            given(keywordBoostProperties.enabled()).willReturn(true);
            given(keywordExtractor.extract("월세")).willReturn(List.of("월세"));
            given(embeddingProvider.embed("월세")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(
                    eq(1L), eq(queryEmbedding), eq(List.of("월세")), eq(10)))
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

            given(keywordBoostProperties.enabled()).willReturn(true);
            given(keywordExtractor.extract("HOUSING")).willReturn(List.of("HOUSING"));
            given(embeddingProvider.embed("HOUSING")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(
                    eq(1L), eq(queryEmbedding), eq(List.of("HOUSING")), eq(10)))
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

            given(keywordBoostProperties.enabled()).willReturn(true);
            given(keywordExtractor.extract("존재하지않는키워드")).willReturn(List.of("존재하지않는키워드"));
            given(embeddingProvider.embed("존재하지않는키워드")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(
                    eq(1L), eq(queryEmbedding), eq(List.of("존재하지않는키워드")), eq(10)))
                    .willReturn(List.of());
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(allChunks);

            // when
            List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("keyword-boost 비활성화 시 keywords 는 빈 리스트로 전달된다")
        void keywordBoostDisabled_passesEmptyKeywords() {
            SearchChunksCommand command = new SearchChunksCommand(1L, "디딤씨앗통장 중복");
            float[] queryEmbedding = new float[]{0.1f};
            List<SimilarChunk> similar = List.of(
                    createSimilarChunk(1L, 1L, 0, "표 항목 디딤씨앗통장", 0.5)
            );

            given(keywordBoostProperties.enabled()).willReturn(false);
            given(embeddingProvider.embed("디딤씨앗통장 중복")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(
                    eq(1L), eq(queryEmbedding), eq(List.of()), eq(10)))
                    .willReturn(similar);

            List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

            assertThat(result).hasSize(1);
            verify(keywordExtractor, never()).extract(any());
        }

        @Test
        @DisplayName("keyword-boost 활성화 시 추출된 키워드를 repository 에 전달한다")
        void keywordBoostEnabled_passesExtractedKeywords() {
            SearchChunksCommand command = new SearchChunksCommand(1L, "디딤씨앗통장 중복 가능?");
            float[] queryEmbedding = new float[]{0.1f};
            List<String> extracted = List.of("디딤씨앗통장", "중복");
            List<SimilarChunk> similar = List.of(
                    createSimilarChunk(1L, 1L, 0, "표 항목 디딤씨앗통장", 0.5)
            );

            given(keywordBoostProperties.enabled()).willReturn(true);
            given(keywordExtractor.extract("디딤씨앗통장 중복 가능?")).willReturn(extracted);
            given(embeddingProvider.embed("디딤씨앗통장 중복 가능?")).willReturn(queryEmbedding);
            given(policyDocumentRepository.findSimilarByEmbedding(
                    eq(1L), eq(queryEmbedding), eq(extracted), eq(10)))
                    .willReturn(similar);

            List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

            assertThat(result).hasSize(1);
            verify(keywordExtractor).extract("디딤씨앗통장 중복 가능?");
        }
    }

    @Nested
    @DisplayName("Hybrid 검색 path")
    class HybridPath {

        @Test
        @DisplayName("hybrid.enabled=false 면 기존 vector-only path 동작")
        void hybridDisabled_usesVectorOnly() {
            SearchChunksCommand command = new SearchChunksCommand(1L, "query");
            float[] embedding = new float[]{0.1f, 0.2f};
            given(hybridSearchProperties.enabled()).willReturn(false);
            given(keywordBoostProperties.enabled()).willReturn(false);
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(embedding), any(), eq(10)))
                    .willReturn(List.of(new SimilarChunk(10L, 1L, 0, "c", null, null, null, 0.2)));

            List<PolicyDocumentChunkResult> result =
                    ragSearchService.searchRelevantChunks(command, embedding);

            assertThat(result).hasSize(1);
            verify(trigramSearchExecutor, never()).searchTopByTrigram(any(), any(), anyDouble(), anyInt());
            verify(reciprocalRankFusion, never()).merge(any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("hybrid.enabled=true 면 vector + trigram 호출 후 RRF 결합")
        void hybridEnabled_callsRrf() {
            SearchChunksCommand command = new SearchChunksCommand(1L, "희망저축계좌");
            float[] embedding = new float[]{0.1f, 0.2f};
            given(hybridSearchProperties.enabled()).willReturn(true);
            given(hybridSearchProperties.topNPerSearch()).willReturn(20);
            given(hybridSearchProperties.rrfK()).willReturn(60);
            given(hybridSearchProperties.trigramThreshold()).willReturn(0.1);
            given(keywordBoostProperties.enabled()).willReturn(false);

            List<SimilarChunk> vecChunks = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            List<SimilarChunk> triChunks = List.of(new SimilarChunk(20L, 1L, 1, "t", null, null, null, 0.7));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(embedding), any(), eq(20)))
                    .willReturn(vecChunks);
            given(trigramSearchExecutor.searchTopByTrigram(eq(1L), eq("희망저축계좌"), eq(0.1), eq(20)))
                    .willReturn(triChunks);

            SimilarChunk merged = new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2);
            given(reciprocalRankFusion.merge(any(), any(), eq(60), eq(10)))
                    .willReturn(List.of(merged));

            List<PolicyDocumentChunkResult> result =
                    ragSearchService.searchRelevantChunks(command, embedding);

            assertThat(result).hasSize(1);
            verify(reciprocalRankFusion).merge(any(), any(), eq(60), eq(10));
        }

        @Test
        @DisplayName("trigram 쿼리 예외 발생 시 vector 단독 결과로 RRF 를 통과한다")
        void trigramFailure_fallsBackToVector() {
            SearchChunksCommand command = new SearchChunksCommand(1L, "쿼리");
            float[] embedding = new float[]{0.1f};
            given(hybridSearchProperties.enabled()).willReturn(true);
            given(hybridSearchProperties.topNPerSearch()).willReturn(20);
            given(hybridSearchProperties.rrfK()).willReturn(60);
            given(hybridSearchProperties.trigramThreshold()).willReturn(0.1);
            given(keywordBoostProperties.enabled()).willReturn(false);

            List<SimilarChunk> vecChunks = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(embedding), any(), eq(20)))
                    .willReturn(vecChunks);
            given(trigramSearchExecutor.searchTopByTrigram(eq(1L), eq("쿼리"), eq(0.1), eq(20)))
                    .willThrow(new RuntimeException("trigram down"));
            given(reciprocalRankFusion.merge(eq(vecChunks), eq(List.of()), eq(60), eq(10)))
                    .willReturn(vecChunks);

            List<PolicyDocumentChunkResult> result =
                    ragSearchService.searchRelevantChunks(command, embedding);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(10L);
            verify(reciprocalRankFusion).merge(eq(vecChunks), eq(List.of()), eq(60), eq(10));
        }

        @Test
        @DisplayName("trigram 결과 0건이어도 hybrid path 는 DEFAULT_TOP_K(10) 로 컷한다")
        void hybridEnabled_trigramEmpty_appliesTopKLimit() {
            SearchChunksCommand command = new SearchChunksCommand(1L, "쿼리");
            float[] embedding = new float[]{0.1f};
            given(hybridSearchProperties.enabled()).willReturn(true);
            given(hybridSearchProperties.topNPerSearch()).willReturn(20);
            given(hybridSearchProperties.rrfK()).willReturn(60);
            given(hybridSearchProperties.trigramThreshold()).willReturn(0.1);
            given(keywordBoostProperties.enabled()).willReturn(false);

            // vector 20개 반환, trigram 0건
            List<SimilarChunk> vec20 = new java.util.ArrayList<>();
            for (long i = 0; i < 20; i++) {
                vec20.add(new SimilarChunk(i, 1L, (int) i, "c" + i, null, null, null, 0.1 + i * 0.01));
            }
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(embedding), any(), eq(20)))
                    .willReturn(vec20);
            given(trigramSearchExecutor.searchTopByTrigram(eq(1L), eq("쿼리"), eq(0.1), eq(20)))
                    .willReturn(List.of());

            // RRF mock 이 단독-call 케이스에서 DEFAULT_TOP_K=10 로 컷한 결과 반환
            given(reciprocalRankFusion.merge(eq(vec20), eq(List.of()), eq(60), eq(10)))
                    .willReturn(vec20.subList(0, 10));

            List<PolicyDocumentChunkResult> result =
                    ragSearchService.searchRelevantChunks(command, embedding);

            assertThat(result).hasSize(10);
            verify(reciprocalRankFusion).merge(eq(vec20), eq(List.of()), eq(60), eq(10));
        }
    }

    @Nested
    @DisplayName("searchRelevantChunksWithTrace - 어드민 미리보기")
    class WithTrace {

        @Test
        @DisplayName("overrides null 이면 운영 properties 값으로 검색한다")
        void nullOverrides_usesBaselineProperties() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거");
            float[] emb = new float[]{0.1f};
            EffectiveConfig baseConfig = new EffectiveConfig(true, 20, 60, 0.10, false, 5);
            given(effectiveConfigFactory.baseline(null)).willReturn(baseConfig);

            List<SimilarChunk> vec = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), any(), eq(20)))
                    .willReturn(vec);
            given(trigramSearchExecutor.searchTopByTrigram(eq(1L), eq("주거"), eq(0.10), eq(20)))
                    .willReturn(List.of());
            given(reciprocalRankFusion.merge(eq(vec), eq(List.of()), eq(60), eq(10)))
                    .willReturn(vec);

            RagSearchTrace trace =
                    ragSearchService.searchRelevantChunksWithTrace(cmd, emb, null);

            assertThat(trace.effective().rrfK()).isEqualTo(60);
            assertThat(trace.effective().topNPerSearch()).isEqualTo(20);
            assertThat(trace.vectorTopN()).hasSize(1);
            assertThat(trace.merged()).hasSize(1);
            assertThat(trace.merged().get(0).rank()).isEqualTo(1);
            assertThat(trace.tookMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("overrides 의 rrfK 만 지정하면 그 값으로 RRF 가 호출된다")
        void overridesRrfK_passedToRrf() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거");
            float[] emb = new float[]{0.1f};
            HybridSearchOverrides ov = new HybridSearchOverrides(
                    null, null, 30, null, null, null);
            EffectiveConfig overrideConfig = new EffectiveConfig(true, 20, 30, 0.10, false, 5);
            given(effectiveConfigFactory.baseline(ov)).willReturn(overrideConfig);

            List<SimilarChunk> vec = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), any(), eq(20)))
                    .willReturn(vec);
            given(trigramSearchExecutor.searchTopByTrigram(eq(1L), eq("주거"), eq(0.10), eq(20)))
                    .willReturn(List.of());
            given(reciprocalRankFusion.merge(eq(vec), eq(List.of()), eq(30), eq(10)))
                    .willReturn(vec);

            RagSearchTrace trace =
                    ragSearchService.searchRelevantChunksWithTrace(cmd, emb, ov);

            assertThat(trace.effective().rrfK()).isEqualTo(30);
            verify(reciprocalRankFusion).merge(eq(vec), eq(List.of()), eq(30), eq(10));
        }

        @Test
        @DisplayName("overrides.hybridEnabled=false 면 vector-only 경로, trigramTopN 은 빈 리스트")
        void hybridDisabledOverride_returnsVectorOnly() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거");
            float[] emb = new float[]{0.1f};
            HybridSearchOverrides ov = new HybridSearchOverrides(
                    false, null, null, null, null, null);
            // baseline 은 hybrid=true 인데 override 로 끔
            EffectiveConfig overrideConfig = new EffectiveConfig(false, 20, 60, 0.10, false, 5);
            given(effectiveConfigFactory.baseline(ov)).willReturn(overrideConfig);

            List<SimilarChunk> vec = List.of(
                    new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), any(), eq(10)))
                    .willReturn(vec);

            RagSearchTrace trace =
                    ragSearchService.searchRelevantChunksWithTrace(cmd, emb, ov);

            assertThat(trace.effective().hybridEnabled()).isFalse();
            assertThat(trace.trigramTopN()).isEmpty();
            assertThat(trace.merged()).hasSize(1);
            verify(trigramSearchExecutor, never()).searchTopByTrigram(any(), any(), anyDouble(), anyInt());
            verify(reciprocalRankFusion, never()).merge(any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("overrides.keywordBoostEnabled=false 면 keywords 빈 리스트로 호출")
        void keywordBoostDisabledOverride_passesEmptyKeywords() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거 지원");
            float[] emb = new float[]{0.1f};
            HybridSearchOverrides ov = new HybridSearchOverrides(
                    null, null, null, null, false, null);
            // baseline 은 keywordBoost=true 인데 override 로 끔
            EffectiveConfig overrideConfig = new EffectiveConfig(true, 20, 60, 0.10, false, 5);
            given(effectiveConfigFactory.baseline(ov)).willReturn(overrideConfig);

            List<SimilarChunk> vec = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), eq(List.of()), eq(20)))
                    .willReturn(vec);
            given(trigramSearchExecutor.searchTopByTrigram(eq(1L), eq("주거 지원"), eq(0.10), eq(20)))
                    .willReturn(List.of());
            given(reciprocalRankFusion.merge(eq(vec), eq(List.of()), eq(60), eq(10)))
                    .willReturn(vec);

            ragSearchService.searchRelevantChunksWithTrace(cmd, emb, ov);

            verify(keywordExtractor, never()).extract(any());
            verify(policyDocumentRepository).findSimilarByEmbedding(
                    eq(1L), eq(emb), eq(List.of()), eq(20));
        }

        @Test
        @DisplayName("trigram 예외 발생 시에도 trace.trigramTopN 은 빈 리스트로 반환")
        void trigramFailure_returnsEmptyTrigramTopN() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거");
            float[] emb = new float[]{0.1f};
            EffectiveConfig baseConfig = new EffectiveConfig(true, 20, 60, 0.10, false, 5);
            given(effectiveConfigFactory.baseline(null)).willReturn(baseConfig);

            List<SimilarChunk> vec = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), any(), eq(20)))
                    .willReturn(vec);
            given(trigramSearchExecutor.searchTopByTrigram(eq(1L), eq("주거"), eq(0.10), eq(20)))
                    .willThrow(new RuntimeException("trigram down"));
            given(reciprocalRankFusion.merge(eq(vec), eq(List.of()), eq(60), eq(10)))
                    .willReturn(vec);

            RagSearchTrace trace =
                    ragSearchService.searchRelevantChunksWithTrace(cmd, emb, null);

            assertThat(trace.trigramTopN()).isEmpty();
            assertThat(trace.vectorTopN()).hasSize(1);
        }
    }

    // ── 헬퍼 메서드 ──

    private PolicyDocument createChunkWithId(Long id, Long policyId, int index, String content) {
        PolicyDocument chunk = PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(index)
                .content(content)
                .sourceHash("test-hash")
                .source(PolicyDocumentSource.BODY)
                .build();
        ReflectionTestUtils.setField(chunk, "id", id);
        return chunk;
    }

    private SimilarChunk createSimilarChunk(Long id, Long policyId, int index, String content, double distance) {
        return new SimilarChunk(id, policyId, index, content, null, null, null, distance);
    }
}
