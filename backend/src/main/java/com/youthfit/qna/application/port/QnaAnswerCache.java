package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.result.CachedAnswer;

import java.util.Optional;

public interface QnaAnswerCache {

    Optional<CachedAnswer> get(Long policyId, String question);

    void put(Long policyId, String question, CachedAnswer value);
}
