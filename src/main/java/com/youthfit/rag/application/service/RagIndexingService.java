package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import com.youthfit.rag.domain.service.DocumentChunker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagIndexingService {

    private final PolicyDocumentRepository policyDocumentRepository;
    private final DocumentChunker documentChunker;

    @Transactional
    public IndexingResult indexPolicyDocument(IndexPolicyDocumentCommand command) {
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
        policyDocumentRepository.saveAll(chunks);

        return new IndexingResult(command.policyId(), chunks.size(), true);
    }
}
