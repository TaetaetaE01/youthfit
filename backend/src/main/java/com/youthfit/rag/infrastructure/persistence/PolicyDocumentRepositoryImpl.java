package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
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
    public List<SimilarChunk> findSimilarByEmbedding(Long policyId, float[] queryEmbedding,
                                                      List<String> keywords, int limit) {
        String vectorString = toVectorString(queryEmbedding);
        String[] keywordArray = keywords == null ? new String[0] : keywords.toArray(new String[0]);
        return jpaRepository.findSimilarByEmbedding(policyId, vectorString, keywordArray, limit).stream()
                .map(this::toSimilarChunk)
                .toList();
    }

    @Override
    public Optional<String> findSourceHashByPolicyId(Long policyId) {
        return jpaRepository.findDistinctSourceHashByPolicyId(policyId).stream().findFirst();
    }

    @Override
    public List<SimilarChunk> findTopByTrigram(Long policyId, String query, double threshold, int limit) {
        return jpaRepository.findTopByTrigram(policyId, query, threshold, limit).stream()
                .map(this::toTrigramChunk)
                .toList();
    }

    private SimilarChunk toTrigramChunk(Object[] row) {
        // sim (0~1) 을 distance 필드에 그대로 담아 반환한다.
        // 후속 ReciprocalRankFusion 가 trigram-only 청크의 distance 를 1.0-sim 으로 변환한다.
        double sim = ((Number) row[7]).doubleValue();
        return new SimilarChunk(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).intValue(),
                (String) row[3],
                row[4] == null ? null : ((Number) row[4]).longValue(),
                row[5] == null ? null : ((Number) row[5]).intValue(),
                row[6] == null ? null : ((Number) row[6]).intValue(),
                sim
        );
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
