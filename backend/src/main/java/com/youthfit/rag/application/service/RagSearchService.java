package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.MergedChunk;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.dto.result.SimilarChunkResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import com.youthfit.rag.domain.service.KeywordExtractor;
import com.youthfit.rag.domain.service.ReciprocalRankFusion;
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
import com.youthfit.rag.infrastructure.config.KeywordBoostProperties;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);
    private static final int DEFAULT_TOP_K = 10;

    private final PolicyDocumentRepository policyDocumentRepository;
    private final EmbeddingProvider embeddingProvider;
    private final KeywordExtractor keywordExtractor;
    private final KeywordBoostProperties keywordBoostProperties;
    private final HybridSearchProperties hybridSearchProperties;
    private final ReciprocalRankFusion reciprocalRankFusion;
    private final EffectiveConfigFactory effectiveConfigFactory;

    @Transactional(readOnly = true)
    public List<PolicyDocumentChunkResult> searchRelevantChunks(SearchChunksCommand command) {
        if (command.query() == null || command.query().isBlank()) {
            return policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId()).stream()
                    .map(PolicyDocumentChunkResult::from)
                    .toList();
        }
        float[] queryEmbedding = embeddingProvider.embed(command.query());
        return searchRelevantChunks(command, queryEmbedding);
    }

    @Transactional(readOnly = true)
    public List<PolicyDocumentChunkResult> searchRelevantChunks(SearchChunksCommand command,
                                                                float[] precomputedEmbedding) {
        if (command.query() == null || command.query().isBlank()) {
            return policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId()).stream()
                    .map(PolicyDocumentChunkResult::from)
                    .toList();
        }

        List<String> keywords = keywordBoostProperties.enabled()
                ? keywordExtractor.extract(command.query())
                : List.of();

        if (hybridSearchProperties.enabled()) {
            // Delegate to the shared hybrid algorithm. The trace's merged list is the
            // authoritative result; if it's empty, apply production keyword fallback.
            // Build EffectiveConfig directly from properties (no override for production path).
            EffectiveConfig config = new EffectiveConfig(
                    hybridSearchProperties.enabled(),
                    hybridSearchProperties.topNPerSearch(),
                    hybridSearchProperties.rrfK(),
                    hybridSearchProperties.trigramThreshold(),
                    keywordBoostProperties.enabled(),
                    keywordBoostProperties.maxKeywords());
            RagSearchTrace trace = runHybridSearchInternal(command, precomputedEmbedding, config, keywords);
            if (trace.merged().isEmpty()) {
                log.info("hybrid 검색 결과 없음, 키워드 폴백 수행: policyId={}", command.policyId());
                return fallbackKeywordSearch(command);
            }
            if (log.isInfoEnabled()) {
                log.info("hybrid 검색: policyId={}, vector_top{}={}, trigram_top{}={}, merged_top{}={}",
                        command.policyId(),
                        trace.vectorTopN().size(), summarizeResults(trace.vectorTopN()),
                        trace.trigramTopN().size(), summarizeResults(trace.trigramTopN()),
                        trace.merged().size(), summarizeMerged(trace.merged()));
            }
            return trace.merged().stream()
                    .map(c -> new PolicyDocumentChunkResult(
                            c.chunkId(), null, c.chunkIndex(), c.preview(), c.distance(), null, null, null))
                    .toList();
        }

        if (log.isInfoEnabled()) {
            log.info("RAG 키워드 boost: policyId={}, enabled={}, keywords={}",
                    command.policyId(), keywordBoostProperties.enabled(), keywords);
        }

        List<SimilarChunk> similar = policyDocumentRepository.findSimilarByEmbedding(
                command.policyId(), precomputedEmbedding, keywords, DEFAULT_TOP_K);

        if (similar.isEmpty()) {
            log.info("벡터 검색 결과 없음, 키워드 폴백 수행: policyId={}", command.policyId());
            return fallbackKeywordSearch(command);
        }

        if (log.isInfoEnabled()) {
            String distanceSummary = similar.stream()
                    .map(c -> String.format("%.3f", c.distance()))
                    .toList()
                    .toString();
            log.info("RAG 검색 결과: policyId={}, top{}={}", command.policyId(), similar.size(), distanceSummary);
        }

        return similar.stream()
                .map(PolicyDocumentChunkResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RagSearchTrace searchRelevantChunksWithTrace(
            SearchChunksCommand command,
            float[] precomputedEmbedding,
            @Nullable HybridSearchOverrides overrides
    ) {
        EffectiveConfig effective = effectiveConfigFactory.baseline(overrides);

        List<String> keywords = effective.keywordBoostEnabled()
                ? keywordExtractor.extract(command.query())
                : List.of();

        if (!effective.hybridEnabled()) {
            // vector-only 경로 — DEFAULT_TOP_K 컷
            long start = System.currentTimeMillis();
            List<SimilarChunk> vec = policyDocumentRepository.findSimilarByEmbedding(
                    command.policyId(), precomputedEmbedding, keywords, DEFAULT_TOP_K);
            List<SimilarChunkResult> vecResults = vec.stream().map(SimilarChunkResult::from).toList();
            List<MergedChunk> merged = toMergedChunksFromVectorOnly(vec);
            return new RagSearchTrace(effective, vecResults, List.of(), merged, keywords,
                    System.currentTimeMillis() - start);
        }

        // Delegate to the shared hybrid algorithm — no fallback for admin (shows raw empty-result reality).
        return runHybridSearchInternal(command, precomputedEmbedding, effective, keywords);
    }

    /**
     * Core hybrid search algorithm shared by both the production path and the admin trace path.
     *
     * <p>Drift guarantee: vector + trigram fetch, trigram-failure handling, and RRF merge
     * are all in this single method. Neither public method duplicates this logic.</p>
     *
     * <p>Design note: production gets a keyword fallback on empty merged result (UX),
     * admin sees the raw empty-result reality (tuning truth). The fallback decision
     * is intentionally left to each caller.</p>
     */
    private RagSearchTrace runHybridSearchInternal(
            SearchChunksCommand command,
            float[] embedding,
            EffectiveConfig config,
            List<String> keywords
    ) {
        long start = System.currentTimeMillis();
        int topN = config.topNPerSearch();
        int k = config.rrfK();
        double threshold = config.trigramThreshold();

        List<SimilarChunk> vec = policyDocumentRepository.findSimilarByEmbedding(
                command.policyId(), embedding, keywords, topN);

        List<SimilarChunk> tri;
        try {
            tri = policyDocumentRepository.findTopByTrigram(
                    command.policyId(), command.query(), threshold, topN);
        } catch (RuntimeException e) {
            log.warn("trigram 쿼리 실패, vector 결과로 폴백: policyId={}, error={}",
                    command.policyId(), e.toString());
            tri = List.of();
        }

        List<SimilarChunk> mergedSimilar =
                reciprocalRankFusion.merge(vec, tri, k, DEFAULT_TOP_K);
        List<MergedChunk> merged = toMergedChunks(mergedSimilar);

        List<SimilarChunkResult> vecResults = vec.stream().map(SimilarChunkResult::from).toList();
        List<SimilarChunkResult> triResults = tri.stream().map(SimilarChunkResult::from).toList();

        return new RagSearchTrace(config, vecResults, triResults, merged, keywords,
                System.currentTimeMillis() - start);
    }

    private List<MergedChunk> toMergedChunks(List<SimilarChunk> chunks) {
        List<MergedChunk> out = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            SimilarChunk c = chunks.get(i);
            // V1: rrfScore 는 RRF 결과에서 별도 전달 안 되므로 0.0 으로 둠 (UI 는 distance 위주).
            // 추후 ReciprocalRankFusion 이 score 도 함께 반환하도록 확장 가능.
            out.add(new MergedChunk(c.id(), c.chunkIndex(), c.distance(), 0.0, i + 1,
                    truncate(c.content(), 500)));
        }
        return out;
    }

    private List<MergedChunk> toMergedChunksFromVectorOnly(List<SimilarChunk> chunks) {
        return toMergedChunks(chunks);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String summarizeResults(List<SimilarChunkResult> chunks) {
        return chunks.stream()
                .map(c -> String.format("%.3f", c.distance()))
                .toList()
                .toString();
    }

    private String summarizeMerged(List<MergedChunk> chunks) {
        return chunks.stream()
                .map(c -> String.format("%.3f", c.distance()))
                .toList()
                .toString();
    }

    private List<PolicyDocumentChunkResult> fallbackKeywordSearch(SearchChunksCommand command) {
        String lowerQuery = command.query().toLowerCase();
        return policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId()).stream()
                .filter(chunk -> chunk.getContent().toLowerCase().contains(lowerQuery))
                .map(PolicyDocumentChunkResult::from)
                .toList();
    }
}
