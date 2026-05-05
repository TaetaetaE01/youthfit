package com.youthfit.qna.infrastructure.scheduler;

import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class QnaCacheLookupRetentionScheduler {

    private final QnaCacheLookupLogRepository repository;
    private final QnaProperties qnaProperties;

    @Transactional
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void purgeOldLookups() {
        int days = qnaProperties.cache().retentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int deleted = repository.deleteOlderThan(cutoff);
        log.info("Q&A 캐시 lookup 로그 retention: cutoff={}, deleted={}", cutoff, deleted);
    }
}
