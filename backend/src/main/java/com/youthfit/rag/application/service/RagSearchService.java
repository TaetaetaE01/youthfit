package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
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
    private static final int DEFAULT_TOP_K = 5;

    private final PolicyDocumentRepository policyDocumentRepository;
    private final EmbeddingProvider embeddingProvider;

    @Transactional(readOnly = true)
    public List<PolicyDocumentChunkResult> searchRelevantChunks(SearchChunksCommand command) {
        if (command.query() == null || command.query().isBlank()) {
            return policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId()).stream()
                    .map(PolicyDocumentChunkResult::from)
                    .toList();
        }

        float[] queryEmbedding = embeddingProvider.embed(command.query());
        List<SimilarChunk> similar = policyDocumentRepository.findSimilarByEmbedding(
                command.policyId(), queryEmbedding, DEFAULT_TOP_K);

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

    private List<PolicyDocumentChunkResult> fallbackKeywordSearch(SearchChunksCommand command) {
        String lowerQuery = command.query().toLowerCase();
        return policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId()).stream()
                .filter(chunk -> chunk.getContent().toLowerCase().contains(lowerQuery))
                .map(PolicyDocumentChunkResult::from)
                .toList();
    }
}
