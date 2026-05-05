package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.port.dto.SemanticLookupResult;

public interface SemanticQnaCache {

    /**
     * TTL 안에서 가장 가까운 캐시 항목을 항상 반환한다.
     * 임계값 비교는 호출부(Service) 책임이다.
     * userQuestion 은 hit/miss 로그에 기록된다.
     */
    SemanticLookupResult findSimilar(Long policyId, String userQuestion, float[] queryEmbedding);

    void put(Long policyId, String question, String sourceHash, float[] embedding, CachedAnswer answer);
}
