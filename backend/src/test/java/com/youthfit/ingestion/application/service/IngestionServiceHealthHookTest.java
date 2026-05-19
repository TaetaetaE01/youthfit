package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.model.IngestionRunLog;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * receivePolicy try/catch/finally hook 적재 통합 테스트.
 *
 * <p>본 프로젝트는 임베디드 DB 인프라(H2 등)가 없어 {@code @SpringBootTest} 가
 * PostgreSQL 연결을 시도하다 실패한다. 또한 쿼리가 PostgreSQL native syntax 를
 * 사용하므로 H2 로 대체할 수 없다.
 *
 * <p>Stage G (통합 검증) 에서 실 DB 환경이 갖춰진 뒤 {@code @Disabled} 를 제거한다.
 * 실행 방법: {@code DB_PASSWORD=changeme ./gradlew test --tests "*.IngestionServiceHealthHookTest"}
 */
@Disabled("외부 인프라(PostgreSQL) 연결이 필요하여 CI에서 제외 — Stage G 에서 @Disabled 제거")
@SpringBootTest
@ActiveProfiles("test")
class IngestionServiceHealthHookTest {

    @Autowired
    IngestionService service;

    @Autowired
    IngestionRunLogRepository runLogRepo;

    @Autowired
    IngestionItemFailureRepository failureRepo;

    @BeforeEach
    void setUp() {
        runLogRepo.deleteAll();
        failureRepo.deleteAll();
    }

    private IngestPolicyCommand validCommand(String externalId) {
        return new IngestPolicyCommand(
                "https://example.test/policy/" + externalId, "YOUTH_SEOUL_CRAWL",
                LocalDateTime.now(), externalId, "테스트 정책 " + externalId,
                "요약", "[개요]\n본문\n", "복지", "전국",
                LocalDate.now(), LocalDate.now().plusDays(30),
                2026, "ANNUAL", "CASH", "테스트기관", "02-0000-0000",
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null
        );
    }

    @Test
    void 성공_적재는_RunLog_success_1_을_적재한다() {
        service.receivePolicy(validCommand("ext-1"));

        List<IngestionRunLog> logs = runLogRepo.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getSource()).isEqualTo("YOUTH_SEOUL_CRAWL");
        assertThat(logs.get(0).getNormalizedSuccessCount()).isEqualTo(1);
        assertThat(logs.get(0).getNormalizedFailureCount()).isZero();

        assertThat(failureRepo.findAll()).isEmpty();
    }

    @Test
    void 동일_externalId_재수신은_duplicate_count_1_로_적재된다() {
        service.receivePolicy(validCommand("ext-2"));
        runLogRepo.deleteAll();
        service.receivePolicy(validCommand("ext-2"));

        List<IngestionRunLog> logs = runLogRepo.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getDuplicateCount()).isEqualTo(1);
        assertThat(logs.get(0).getNormalizedSuccessCount()).isZero();
    }

    @Test
    void 잘못된_payload_는_RunLog_failure_와_ItemFailure_를_적재한다() {
        // category=null → mapCategory(null) 에서 switch on null → NullPointerException (RuntimeException)
        // Java switch expression on null throws NullPointerException, which is a RuntimeException
        // — 이를 통해 catch 블록을 경유해 IngestionItemFailure 와 failure RunLog 를 적재함을 검증한다.
        IngestPolicyCommand bad = new IngestPolicyCommand(
                null, "BAD_SOURCE", null, null,
                null, "x", null,
                null, "전국", null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null
        );

        assertThatThrownBy(() -> service.receivePolicy(bad))
                .isInstanceOf(RuntimeException.class);

        List<IngestionRunLog> logs = runLogRepo.findAll();
        List<IngestionItemFailure> failures = failureRepo.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getNormalizedFailureCount()).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getSource()).isEqualTo("BAD_SOURCE");
        assertThat(failures.get(0).isPayloadAvailable()).isTrue();
    }
}
