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
            WITH base AS (
              SELECT id, policy_id, chunk_index, content,
                     attachment_id, page_start, page_end,
                     (embedding <=> cast(:queryEmbedding AS vector)) AS distance
                FROM policy_document
               WHERE policy_id = :policyId
                 AND embedding IS NOT NULL
            ), scored AS (
              SELECT b.*,
                     (SELECT COUNT(*) FROM unnest(cast(:keywords AS text[])) k
                       WHERE b.content ILIKE '%' || k || '%') AS hit_count
                FROM base b
            )
            SELECT id,
                   policy_id     AS policyId,
                   chunk_index   AS chunkIndex,
                   content,
                   attachment_id AS attachmentId,
                   page_start    AS pageStart,
                   page_end      AS pageEnd,
                   CASE
                     WHEN hit_count >= 3 THEN distance * 0.78
                     WHEN hit_count = 2 THEN distance * 0.85
                     WHEN hit_count = 1 THEN distance * 0.92
                     ELSE distance
                   END AS distance
              FROM scored
             ORDER BY 8
             LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findSimilarByEmbedding(
            @Param("policyId") Long policyId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("keywords") String[] keywords,
            @Param("limit") int limit
    );

    @Query("SELECT DISTINCT pd.sourceHash FROM PolicyDocument pd WHERE pd.policyId = :policyId")
    List<String> findDistinctSourceHashByPolicyId(@Param("policyId") Long policyId);
}
