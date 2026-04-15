package com.youthfit.rag.domain.repository;

import com.youthfit.rag.domain.model.PolicyDocument;

import java.util.List;

public interface PolicyDocumentRepository {

    PolicyDocument save(PolicyDocument policyDocument);

    List<PolicyDocument> saveAll(List<PolicyDocument> policyDocuments);

    List<PolicyDocument> findByPolicyId(Long policyId);

    List<PolicyDocument> findByPolicyIdOrderByChunkIndex(Long policyId);

    void deleteByPolicyId(Long policyId);

    List<PolicyDocument> findSimilarByEmbedding(Long policyId, float[] queryEmbedding, int limit);
}
