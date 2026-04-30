package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.StringJoiner;

@Repository
@RequiredArgsConstructor
public class PolicyDocumentRepositoryImpl implements PolicyDocumentRepository {

    private final PolicyDocumentJpaRepository jpaRepository;

    @Override
    public PolicyDocument save(PolicyDocument policyDocument) {
        return jpaRepository.save(policyDocument);
    }

    @Override
    public List<PolicyDocument> saveAll(List<PolicyDocument> policyDocuments) {
        return jpaRepository.saveAll(policyDocuments);
    }

    @Override
    public List<PolicyDocument> findByPolicyId(Long policyId) {
        return jpaRepository.findByPolicyId(policyId);
    }

    @Override
    public List<PolicyDocument> findByPolicyIdOrderByChunkIndex(Long policyId) {
        return jpaRepository.findByPolicyIdOrderByChunkIndex(policyId);
    }

    @Override
    public void deleteByPolicyId(Long policyId) {
        jpaRepository.deleteByPolicyId(policyId);
    }

    @Override
    public List<SimilarChunk> findSimilarByEmbedding(Long policyId, float[] queryEmbedding, int limit) {
        String vectorString = toVectorString(queryEmbedding);
        return jpaRepository.findSimilarByEmbedding(policyId, vectorString, limit).stream()
                .map(this::toSimilarChunk)
                .toList();
    }

    private SimilarChunk toSimilarChunk(Object[] row) {
        return new SimilarChunk(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).intValue(),
                (String) row[3],
                row[4] == null ? null : ((Number) row[4]).longValue(),
                row[5] == null ? null : ((Number) row[5]).intValue(),
                row[6] == null ? null : ((Number) row[6]).intValue(),
                ((Number) row[7]).doubleValue()
        );
    }

    private String toVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : embedding) {
            joiner.add(String.valueOf(v));
        }
        return joiner.toString();
    }
}
