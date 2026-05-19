package com.youthfit.ingestion.infrastructure.scheduler;

import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("외부 인프라(PostgreSQL) 연결 필요 — Stage G 에서 @Disabled 제거")
class IngestionRedactSchedulerTest {

    @Autowired IngestionItemFailureRepository repository;
    @Autowired IngestionRedactScheduler scheduler;

    @BeforeEach
    void cleanUp() { repository.deleteAll(); }

    @Test
    void 일주일_경과_payload_는_redact_되고_30일_경과_행은_삭제된다() {
        // 5일 전 — 그대로 유지
        save("recent", "{\"x\":1}", Instant.now().minus(5, ChronoUnit.DAYS));
        // 10일 전 — payload redact
        save("redact-me", "{\"x\":2}", Instant.now().minus(10, ChronoUnit.DAYS));
        // 35일 전 — 삭제
        save("delete-me", "{\"x\":3}", Instant.now().minus(35, ChronoUnit.DAYS));

        // 테스트 단축: 기본값 그대로 (7일 / 30일) 사용 — 위 fixture 가 자연스레 분기
        scheduler.runDailyRedactAndDelete();

        List<IngestionItemFailure> remaining = repository.findAll();
        assertThat(remaining).hasSize(2);

        IngestionItemFailure recent = remaining.stream().filter(f -> f.getSource().equals("recent")).findFirst().orElseThrow();
        assertThat(recent.getRawPayload()).isNotNull();

        IngestionItemFailure redacted = remaining.stream().filter(f -> f.getSource().equals("redact-me")).findFirst().orElseThrow();
        assertThat(redacted.getRawPayload()).isNull();
        assertThat(redacted.getRawPayloadHash()).isNotBlank();
    }

    private void save(String source, String payload, Instant createdAt) {
        IngestionItemFailure f = IngestionItemFailure.of(
                null, source, "ext", payload, FailureReason.OTHER, "msg",
                null, null, null, null);
        ReflectionTestUtils.setField(f, "createdAt", createdAt);
        repository.save(f);
    }
}
