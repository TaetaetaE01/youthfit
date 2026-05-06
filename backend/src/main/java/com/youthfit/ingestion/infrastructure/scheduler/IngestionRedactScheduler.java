package com.youthfit.ingestion.infrastructure.scheduler;

import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionRedactScheduler {

    private final IngestionItemFailureRepository repository;

    @Value("${youthfit.ingestion.health.payload-redact-days:7}")
    private int redactDays;

    @Value("${youthfit.ingestion.health.failure-retention-days:30}")
    private int retentionDays;

    /**
     * 매일 03:00 KST 실행. payload 7일 redact + failure 30일 행 삭제.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void runDailyRedactAndDelete() {
        Instant redactBefore = Instant.now().minus(Duration.ofDays(redactDays));
        Instant deleteBefore = Instant.now().minus(Duration.ofDays(retentionDays));

        List<IngestionItemFailure> toRedact = repository.findPayloadsToRedact(redactBefore);
        for (IngestionItemFailure f : toRedact) {
            String hash = sha256(f.getRawPayload());
            f.redactPayload(hash);
        }
        repository.saveAll(toRedact);
        log.info("ingestion redact: {} 건 raw_payload → hash", toRedact.size());

        int deleted = repository.deleteOlderThan(deleteBefore);
        log.info("ingestion failure retention 삭제: {} 건", deleted);
    }

    private String sha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "hash_error";
        }
    }
}
