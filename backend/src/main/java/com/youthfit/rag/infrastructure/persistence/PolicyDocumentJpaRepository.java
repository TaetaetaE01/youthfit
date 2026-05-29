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
                   END AS boosted_distance
              FROM scored
             ORDER BY boosted_distance
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

    @Query("""
            select d.policyId, count(distinct d.attachmentId)
            from PolicyDocument d
            where d.policyId in :policyIds
              and d.source = com.youthfit.rag.domain.model.PolicyDocumentSource.ATTACHMENT
            group by d.policyId
            """)
    List<Object[]> countAttachmentEmbeddingsByPolicyIdsRaw(@Param("policyIds") List<Long> policyIds);

    @Query("""
            select distinct d.attachmentId from PolicyDocument d
            where d.policyId = :policyId
              and d.source = com.youthfit.rag.domain.model.PolicyDocumentSource.ATTACHMENT
            """)
    List<Long> findDistinctAttachmentIds(@Param("policyId") Long policyId);

    @Query(value = """
            SELECT id,
                   policy_id     AS policyId,
                   chunk_index   AS chunkIndex,
                   content,
                   attachment_id AS attachmentId,
                   page_start    AS pageStart,
                   page_end      AS pageEnd,
                   similarity(content, :query) AS sim
              FROM policy_document
             WHERE policy_id = :policyId
             ORDER BY content <-> :query
             LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopByTrigram(
            @Param("policyId") Long policyId,
            @Param("query") String query,
            @Param("limit") int limit
    );
}
