package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.rag.domain.model.PolicyDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PolicyDocumentJpaRepository extends JpaRepository<PolicyDocument, Long> {

    List<PolicyDocument> findByPolicyId(Long policyId);

    List<PolicyDocument> findByPolicyIdOrderByChunkIndex(Long policyId);

    void deleteByPolicyId(Long policyId);

    @Query(value = """
            SELECT id,
                   policy_id        AS policyId,
                   chunk_index      AS chunkIndex,
                   content,
                   attachment_id    AS attachmentId,
                   page_start       AS pageStart,
                   page_end         AS pageEnd,
                   (embedding <=> cast(:queryEmbedding AS vector)) AS distance
              FROM policy_document
             WHERE policy_id = :policyId
               AND embedding IS NOT NULL
             ORDER BY distance
             LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findSimilarByEmbedding(
            @Param("policyId") Long policyId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit
    );
}
