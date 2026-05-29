package com.youthfit.rag.domain.repository;

import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.SimilarChunk;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface PolicyDocumentRepository {

    PolicyDocument save(PolicyDocument policyDocument);

    List<PolicyDocument> saveAll(List<PolicyDocument> policyDocuments);

    List<PolicyDocument> findByPolicyId(Long policyId);

    List<PolicyDocument> findByPolicyIdOrderByChunkIndex(Long policyId);

    void deleteByPolicyId(Long policyId);

    List<SimilarChunk> findSimilarByEmbedding(
            Long policyId,
            float[] queryEmbedding,
            List<String> keywords,
            int limit
    );

    Optional<String> findSourceHashByPolicyId(Long policyId);

    List<SimilarChunk> findTopByTrigram(
            Long policyId,
            String query,
            double threshold,
            int limit
    );

    /**
     * 정책 다건의 첨부 임베딩 (source=ATTACHMENT) 개수 일괄 조회.
     *
     * @param policyIds 조회 대상 정책 id 리스트
     * @return policyId -> distinct attachmentId count. 첨부 임베딩이 없는 정책은 결과 맵에서 누락된다.
     */
    Map<Long, Long> countAttachmentEmbeddingsByPolicyIds(List<Long> policyIds);

    /**
     * 펼침용: 정책 1건에서 임베딩 완료된 attachment_id set 반환.
     */
    Set<Long> findEmbeddedAttachmentIds(Long policyId);
}
