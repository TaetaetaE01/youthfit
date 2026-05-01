package com.youthfit.qna.application.service;

import com.youthfit.qna.application.port.QnaCacheInvalidator;
import com.youthfit.qna.domain.repository.QnaQuestionCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultQnaCacheInvalidator implements QnaCacheInvalidator {

    private final QnaQuestionCacheRepository repository;

    @Override
    public void invalidatePolicy(Long policyId) {
        repository.deleteByPolicyId(policyId);
    }
}
