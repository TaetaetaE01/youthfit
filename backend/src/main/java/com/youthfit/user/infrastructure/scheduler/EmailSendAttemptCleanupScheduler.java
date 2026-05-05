package com.youthfit.user.infrastructure.scheduler;

import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
public class EmailSendAttemptCleanupScheduler {

    private final EmailSendAttemptRepository repository;
    private final Clock clock;
    private final int retentionDays;

    public EmailSendAttemptCleanupScheduler(
            EmailSendAttemptRepository repository,
            Clock clock,
            @Value("${youthfit.email.attempt.retention-days:90}") int retentionDays) {
        this.repository = repository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${youthfit.email.attempt.cleanup-cron:0 30 3 * * *}")
    public void cleanup() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusDays(retentionDays);
        int deleted = repository.deleteOlderThan(threshold);
        log.info("EmailSendAttempt 보관기간 정리 완료 retentionDays={} deleted={} threshold={}",
                 retentionDays, deleted, threshold);
    }
}
