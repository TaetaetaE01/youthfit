package com.youthfit.qna.infrastructure.scheduler;

import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QnaCacheLookupRetentionSchedulerTest {

    @Mock QnaCacheLookupLogRepository repository;
    @Mock QnaProperties qnaProperties;
    @Mock QnaProperties.Cache cacheProps;

    @InjectMocks QnaCacheLookupRetentionScheduler scheduler;

    @Test
    void retentionDays_기준으로_삭제_호출() {
        when(qnaProperties.cache()).thenReturn(cacheProps);
        when(cacheProps.retentionDays()).thenReturn(90);
        when(repository.deleteOlderThan(argThat(t -> t.isBefore(LocalDateTime.now().minusDays(89)))))
                .thenReturn(5);

        scheduler.purgeOldLookups();

        verify(repository).deleteOlderThan(argThat(t -> t.isBefore(LocalDateTime.now().minusDays(89))));
    }
}
