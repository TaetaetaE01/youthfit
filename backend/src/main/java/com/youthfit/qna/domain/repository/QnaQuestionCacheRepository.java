package com.youthfit.qna.domain.repository;

import com.youthfit.qna.domain.model.QnaQuestionCache;
import com.youthfit.qna.domain.model.SimilarCachedAnswer;

import java.time.Duration;
import java.util.Optional;

public interface QnaQuestionCacheRepository {

    Optional<SimilarCachedAnswer> findClosestByPolicyId(Long policyId,
                                                        float[] queryEmbedding,
                                                        Duration ttl);

    QnaQuestionCache save(QnaQuestionCache entity);

    void deleteByPolicyId(Long policyId);
}
