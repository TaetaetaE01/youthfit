package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.MergedChunk;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
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

    // ⚠ 알고리즘 동기화: searchRelevantChunksWithTrace 의 hybrid 분기와 1:1 대응해야 한다.
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
            // #173: findTopByTrigram 은 REQUIRES_NEW 로 격리돼 있어 여기서 실패해도
            // 본 메서드의 (readOnly) 바깥 트랜잭션을 aborted 상태로 오염시키지 않는다.
            // 그래서 이 catch 폴백이 실제로 유효하게 동작한다.
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

        // 한쪽 단독 케이스도 RRF 통과 — DEFAULT_TOP_K 컷이 일관되게 적용됨
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

    @Transactional(readOnly = true)
    public RagSearchTrace searchRelevantChunksWithTrace(
            SearchChunksCommand command,
            float[] precomputedEmbedding,
            @Nullable HybridSearchOverrides overrides
    ) {
        long start = System.currentTimeMillis();
        EffectiveConfig effective = effectiveConfigFactory.baseline(overrides);

        List<String> keywords = effective.keywordBoostEnabled()
                ? keywordExtractor.extract(command.query())
                : List.of();

        if (!effective.hybridEnabled()) {
            // vector-only 경로 — DEFAULT_TOP_K 컷
            List<SimilarChunk> vec = policyDocumentRepository.findSimilarByEmbedding(
                    command.policyId(), precomputedEmbedding, keywords, DEFAULT_TOP_K);
            List<MergedChunk> merged = toMergedChunksFromVectorOnly(vec);
            return new RagSearchTrace(effective, vec, List.of(), merged, keywords,
                    System.currentTimeMillis() - start);
        }

        // ⚠ 알고리즘 동기화: 아래 로직은 hybridSearch(...) 와 1:1 대응해야 한다.
        //   둘을 합치지 않는 이유는 운영은 SimilarChunk 컷, 어드민은 MergedChunk 트레이스가 필요하기 때문.
        int topN = effective.topNPerSearch();
        int k = effective.rrfK();
        double threshold = effective.trigramThreshold();

        List<SimilarChunk> vec = policyDocumentRepository.findSimilarByEmbedding(
                command.policyId(), precomputedEmbedding, keywords, topN);

        List<SimilarChunk> tri;
        try {
            // #173: findTopByTrigram 은 REQUIRES_NEW 로 격리돼 있어 여기서 실패해도
            // 본 메서드의 (readOnly) 바깥 트랜잭션을 aborted 상태로 오염시키지 않는다.
            // 그래서 이 catch 폴백이 실제로 유효하게 동작한다.
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

        return new RagSearchTrace(effective, vec, tri, merged, keywords,
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
