package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import com.youthfit.rag.domain.service.KeywordExtractor;
import com.youthfit.rag.domain.service.ReciprocalRankFusion;
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
import com.youthfit.rag.infrastructure.config.KeywordBoostProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            return hybridSearch(command, precomputedEmbedding, keywords);
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

    private List<PolicyDocumentChunkResult> hybridSearch(
            SearchChunksCommand command, float[] embedding, List<String> keywords
    ) {
        int topN = hybridSearchProperties.topNPerSearch();
        int k = hybridSearchProperties.rrfK();
        double threshold = hybridSearchProperties.trigramThreshold();

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

        if (vec.isEmpty() && tri.isEmpty()) {
            log.info("hybrid 검색 결과 없음, 키워드 폴백 수행: policyId={}", command.policyId());
            return fallbackKeywordSearch(command);
        }

        if (tri.isEmpty()) {
            return vec.stream().map(PolicyDocumentChunkResult::from).toList();
        }

        List<SimilarChunk> merged =
                reciprocalRankFusion.merge(vec, tri, k, DEFAULT_TOP_K);

        if (log.isInfoEnabled()) {
            log.info("hybrid 검색: policyId={}, vector_top{}={}, trigram_top{}={}, merged_top{}={}",
                    command.policyId(),
                    vec.size(), summarize(vec),
                    tri.size(), summarize(tri),
                    merged.size(), summarize(merged));
        }

        return merged.stream().map(PolicyDocumentChunkResult::from).toList();
    }

    private String summarize(List<SimilarChunk> chunks) {
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
