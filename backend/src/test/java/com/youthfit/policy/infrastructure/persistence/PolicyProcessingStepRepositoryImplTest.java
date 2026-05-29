package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.common.config.JpaAuditingConfig;
import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PolicyProcessingStepRepositoryImpl.class})
@Testcontainers
@DisplayName("PolicyProcessingStepRepositoryImpl — 일괄 조회 메서드")
class PolicyProcessingStepRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void configureTestDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "CREATE EXTENSION IF NOT EXISTS vector");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private PolicyProcessingStepJpaRepository jpaRepository;

    @Autowired
    private PolicyProcessingStepRepository repository;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
    }

    @Test
    @DisplayName("findLatestStatusMapByPolicyIds — 정책별로 step 의 최신 attempt status 만 반환")
    void findLatestStatusMapByPolicyIds_returnsAllStepsPerPolicy() {
        // policy 100: INGESTION SUCCESS, RAG_INDEXING FAILED → SUCCESS (attempt 2 가 최신)
        persistStep(100L, ProcessingStep.INGESTION, ProcessingStatus.SUCCESS, 1);
        persistStep(100L, ProcessingStep.RAG_INDEXING, ProcessingStatus.FAILED, 1);
        persistStep(100L, ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS, 2);
        // policy 101: ENRICHMENT SKIPPED
        persistStep(101L, ProcessingStep.ENRICHMENT, ProcessingStatus.SKIPPED, 1);

        Map<Long, Map<ProcessingStep, ProcessingStatus>> result =
                repository.findLatestStatusMapByPolicyIds(List.of(100L, 101L));

        assertThat(result.get(100L)).containsEntry(ProcessingStep.INGESTION, ProcessingStatus.SUCCESS);
        assertThat(result.get(100L)).containsEntry(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS);
        assertThat(result.get(101L)).containsEntry(ProcessingStep.ENRICHMENT, ProcessingStatus.SKIPPED);
    }

    @Test
    @DisplayName("findLatestStatusMapByPolicyIds — 빈 입력은 빈 Map 반환")
    void findLatestStatusMapByPolicyIds_emptyInput_returnsEmptyMap() {
        Map<Long, Map<ProcessingStep, ProcessingStatus>> result =
                repository.findLatestStatusMapByPolicyIds(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findLatestRowsByPolicyId — 정책 1건의 step 별 최신 attempt 행만 반환")
    void findLatestRowsByPolicyId_returnsOnlyLatestAttemptPerStep() {
        persistStep(100L, ProcessingStep.RAG_INDEXING, ProcessingStatus.FAILED, 1);
        persistStep(100L, ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS, 2);
        persistStep(100L, ProcessingStep.INGESTION, ProcessingStatus.SUCCESS, 1);

        List<PolicyProcessingStep> rows = repository.findLatestRowsByPolicyId(100L);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(PolicyProcessingStep::getStep)
                .containsExactly(ProcessingStep.INGESTION, ProcessingStep.RAG_INDEXING);
        assertThat(rows).extracting(PolicyProcessingStep::getAttempt)
                .containsExactly(1, 2);
    }

    private void persistStep(Long policyId, ProcessingStep step, ProcessingStatus status, int attempt) {
        PolicyProcessingStep entity = PolicyProcessingStep.start(policyId, step, attempt);
        entity.finish(status, null, null);
        jpaRepository.save(entity);
    }
}
