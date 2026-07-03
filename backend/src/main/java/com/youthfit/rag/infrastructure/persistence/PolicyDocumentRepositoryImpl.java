package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

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

    /**
     * pg_trgm 미설치 등으로 이 쿼리가 실패해도 호출 측(RagSearchService hybrid 경로)의
     * 바깥 readOnly 트랜잭션을 aborted 상태로 오염시키지 않도록 REQUIRES_NEW 로 격리한다
     * (#173). 트레이드오프: 호출마다 커넥션을 잠깐 2개(바깥 tx + 이 tx) 점유한다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<SimilarChunk> findTopByTrigram(Long policyId, String query, double threshold, int limit) {
        double maxDistance = 1.0 - threshold;
        return jpaRepository.findTopByTrigram(policyId, query, limit).stream()
                .map(this::toTrigramChunk)
                .filter(c -> c.distance() <= maxDistance)
                .toList();
    }

    @Override
    public Map<Long, Long> countAttachmentEmbeddingsByPolicyIds(List<Long> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.countAttachmentEmbeddingsByPolicyIdsRaw(policyIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    @Override
    public Set<Long> findEmbeddedAttachmentIds(Long policyId) {
        return new HashSet<>(jpaRepository.findDistinctAttachmentIds(policyId));
    }

    private SimilarChunk toTrigramChunk(Object[] row) {
        // trigram similarity (0~1) 을 distance (0=가까움) 로 변환해 도메인 계약과 일치시킨다.
        double sim = ((Number) row[7]).doubleValue();
        double distance = 1.0 - sim;
        return new SimilarChunk(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).intValue(),
                (String) row[3],
                row[4] == null ? null : ((Number) row[4]).longValue(),
                row[5] == null ? null : ((Number) row[5]).intValue(),
                row[6] == null ? null : ((Number) row[6]).intValue(),
                distance
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
