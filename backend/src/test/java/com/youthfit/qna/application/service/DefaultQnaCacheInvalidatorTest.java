package com.youthfit.qna.application.service;

import com.youthfit.qna.domain.repository.QnaQuestionCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("DefaultQnaCacheInvalidator")
@ExtendWith(MockitoExtension.class)
class DefaultQnaCacheInvalidatorTest {

    @InjectMocks
    private DefaultQnaCacheInvalidator invalidator;

    @Mock
    private QnaQuestionCacheRepository repository;

    @Test
    @DisplayName("invalidatePolicy 는 해당 정책의 의미 캐시를 모두 삭제한다")
    void invalidatePolicy_deletesAll() {
        invalidator.invalidatePolicy(42L);

        verify(repository).deleteByPolicyId(42L);
        verifyNoMoreInteractions(repository);
    }
}
