package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.result.CachedAnswer;

import java.util.Optional;

public interface SemanticQnaCache {

    /**
     * 임계값과 TTL 안에서 가장 가까운 캐시 항목을 반환한다. 임계값을 넘으면 Optional.empty().
     * userQuestion 은 hit/miss 로그에 기록된다.
     */
    Optional<CachedAnswer> findSimilar(Long policyId, String userQuestion, float[] queryEmbedding);

    void put(Long policyId, String question, String sourceHash, float[] embedding, CachedAnswer answer);
}
