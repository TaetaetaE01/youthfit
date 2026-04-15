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
            SELECT * FROM policy_document
            WHERE policy_id = :policyId
              AND embedding IS NOT NULL
            ORDER BY embedding <=> cast(:queryEmbedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<PolicyDocument> findSimilarByEmbedding(
            @Param("policyId") Long policyId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit
    );
}
