package com.youthfit.rag.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import com.youthfit.rag.domain.service.DocumentChunker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagIndexingService {

    private static final Logger log = LoggerFactory.getLogger(RagIndexingService.class);

    private final PolicyDocumentRepository policyDocumentRepository;
    private final DocumentChunker documentChunker;
    private final EmbeddingProvider embeddingProvider;
    private final CostGuard costGuard;

    @Transactional
    public IndexingResult indexPolicyDocument(IndexPolicyDocumentCommand command) {
        if (!costGuard.allows(command.policyId())) {
            costGuard.logSkip("indexPolicyDocument", command.policyId());
            return new IndexingResult(command.policyId(), 0, false);
        }
        String newHash = documentChunker.computeHash(command.content());

        List<PolicyDocument> existing = policyDocumentRepository.findByPolicyId(command.policyId());
        if (!existing.isEmpty()) {
            String existingHash = existing.get(0).getSourceHash();
            if (existingHash.equals(newHash)) {
                return new IndexingResult(command.policyId(), existing.size(), false);
            }
            policyDocumentRepository.deleteByPolicyId(command.policyId());
        }

        List<PolicyDocument> chunks = documentChunker.chunk(command.policyId(), command.content());
        generateEmbeddings(chunks);
        policyDocumentRepository.saveAll(chunks);

        return new IndexingResult(command.policyId(), chunks.size(), true);
    }

    private void generateEmbeddings(List<PolicyDocument> chunks) {
        List<String> texts = chunks.stream()
                .map(PolicyDocument::getContent)
                .toList();

        List<float[]> embeddings = embeddingProvider.embedBatch(texts);

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).updateEmbedding(embeddings.get(i));
        }

        log.info("{}개 청크에 대한 임베딩 생성 완료", chunks.size());
    }
}
