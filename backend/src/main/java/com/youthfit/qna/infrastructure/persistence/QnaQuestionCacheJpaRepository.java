package com.youthfit.qna.infrastructure.persistence;

import com.youthfit.qna.domain.model.QnaQuestionCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QnaQuestionCacheJpaRepository extends JpaRepository<QnaQuestionCache, Long> {

    @Query(value = """
            SELECT id,
                   answer,
                   sources_json AS sourcesJson,
                   (embedding <=> cast(:queryEmbedding AS vector)) AS distance
              FROM qna_question_cache
             WHERE policy_id = :policyId
               AND created_at >= now() - make_interval(hours => :ttlHours)
             ORDER BY distance
             LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findClosestByPolicyId(
            @Param("policyId") Long policyId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("ttlHours") int ttlHours
    );

    @Modifying
    @Query("DELETE FROM QnaQuestionCache c WHERE c.policyId = :policyId")
    void deleteByPolicyId(@Param("policyId") Long policyId);
}
