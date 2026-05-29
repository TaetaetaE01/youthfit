package com.youthfit.admin.integration;

import com.youthfit.admin.application.dto.ReprocessResult;
import com.youthfit.admin.application.service.AdminPolicyProcessingService;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.service.RagIndexingService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdminPolicyProcessingService.reprocess → 이벤트 발행 → listener 4단계 마감 end-to-end 통합 테스트.
 *
 * <p>외부 LLM/임베딩 호출은 {@link MockitoBean} 으로 격리하고 PostgreSQL(pgvector) 은
 * testcontainers 로 띄워 실제 DB 에 IN_PROGRESS → SUCCESS/SKIPPED 전이가 기록되는지 검증한다.</p>
 *
 * <p>Awaitility 로 listener {@code @Async("llmExecutor")} 비동기 처리가 완료될 때까지 폴링한다.</p>
 */
@DisplayName("PolicyReprocess 통합")
@SpringBootTest
@Testcontainers
class PolicyReprocessIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // pgvector + pg_trgm 확장 활성화
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS pg_trgm;");
        // 빈 컨테이너 DB 에 스키마 생성
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Redis 가 실행 중이 아니어도 컨텍스트 부팅이 가능하도록 dummy 주소 지정
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
        // 불필요한 외부 연결 방지
        registry.add("spring.mail.host", () -> "localhost");
    }

    @Autowired private AdminPolicyProcessingService adminService;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyProcessingStepRepository stepRepository;

    @MockitoBean private GuideGenerationService guideGenerationService;
    @MockitoBean private EligibilityRuleGenerationService eligibilityRuleGenerationService;
    @MockitoBean private RagIndexingService ragIndexingService;

    @Test
    @DisplayName("reprocess 호출 시 listener 가 4단계 step 행을 IN_PROGRESS 가 아닌 상태로 마감한다")
    void reprocess_triggersListenerAndFinishesAllSteps() {
        // given — 정책 저장
        Policy policy = Policy.builder()
                .title("통합 테스트 정책")
                .body("본문")
                .category(Category.HOUSING)
                .referenceYear(2026)
                .build();
        Policy saved = policyRepository.save(policy);

        // when
        ReprocessResult result = adminService.reprocess(saved.getId(), "통합 테스트");

        // then — 동기 시점에서 stepIds 4개가 생성됨
        assertThat(result.queued()).isTrue();
        assertThat(result.stepIds()).hasSize(4);

        // listener 가 @Async("llmExecutor") 로 비동기 실행 — Awaitility 로 폴링
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    Map<ProcessingStep, ProcessingStatus> latest = stepRepository
                            .findLatestStatusMapByPolicyIds(List.of(saved.getId()))
                            .getOrDefault(saved.getId(), Map.of());
                    // ENRICHMENT 는 SKIPPED, 나머지는 SUCCESS (mock 이 예외 안 던짐)
                    assertThat(latest.get(ProcessingStep.ENRICHMENT)).isEqualTo(ProcessingStatus.SKIPPED);
                    assertThat(latest.get(ProcessingStep.GUIDE)).isEqualTo(ProcessingStatus.SUCCESS);
                    assertThat(latest.get(ProcessingStep.RULE)).isEqualTo(ProcessingStatus.SUCCESS);
                    assertThat(latest.get(ProcessingStep.RAG_INDEXING)).isEqualTo(ProcessingStatus.SUCCESS);
                });

        // 각 step 행의 finished_at 이 null 아님 (스펙 §7 기준 2번)
        List<PolicyProcessingStep> rows = stepRepository.findLatestRowsByPolicyId(saved.getId());
        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(r -> assertThat(r.getFinishedAt()).isNotNull());
    }
}
