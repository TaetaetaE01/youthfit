package com.youthfit.rag.domain.repository;

import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.SimilarChunk;

import java.util.List;
import java.util.Optional;

public interface PolicyDocumentRepository {

    PolicyDocument save(PolicyDocument policyDocument);

    List<PolicyDocument> saveAll(List<PolicyDocument> policyDocuments);

    List<PolicyDocument> findByPolicyId(Long policyId);

    List<PolicyDocument> findByPolicyIdOrderByChunkIndex(Long policyId);

    void deleteByPolicyId(Long policyId);

    List<SimilarChunk> findSimilarByEmbedding(Long policyId, float[] queryEmbedding, int limit);

    Optional<String> findSourceHashByPolicyId(Long policyId);
}
