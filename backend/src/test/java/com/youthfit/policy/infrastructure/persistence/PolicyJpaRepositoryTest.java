package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.common.config.JpaAuditingConfig;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
@DisplayName("PolicyJpaRepository.findForAdminProcessing — 출처 타입 필터")
class PolicyJpaRepositoryTest {

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
    private PolicyJpaRepository jpaRepository;

    @Autowired
    private PolicySourceJpaRepository sourceJpaRepository;

    private Long policyAId;
    private Long policyBId;

    @BeforeEach
    void setUp() {
        sourceJpaRepository.deleteAll();
        jpaRepository.deleteAll();

        Policy policyA = Policy.builder()
                .title("정책A")
                .category(Category.JOBS)
                .build();
        policyA = jpaRepository.save(policyA);
        policyAId = policyA.getId();

        Policy policyB = Policy.builder()
                .title("정책B")
                .category(Category.JOBS)
                .build();
        policyB = jpaRepository.save(policyB);
        policyBId = policyB.getId();

        sourceJpaRepository.save(PolicySource.builder()
                .policy(policyA)
                .sourceType(SourceType.YOUTH_SEOUL_CRAWL)
                .externalId("ext-a")
                .sourceHash("hash-a")
                .build());

        sourceJpaRepository.save(PolicySource.builder()
                .policy(policyB)
                .sourceType(SourceType.BOKJIRO_CENTRAL)
                .externalId("ext-b")
                .sourceHash("hash-b")
                .build());
    }

    @Test
    @DisplayName("출처타입_필터가_있으면_해당_출처_정책만_반환한다")
    void 출처타입_필터가_있으면_해당_출처_정책만_반환한다() {
        Page<Policy> page = jpaRepository.findForAdminProcessing(
                "", "", SourceType.BOKJIRO_CENTRAL, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Policy::getId).containsExactly(policyBId);
    }

    @Test
    @DisplayName("출처타입_필터가_null이면_전체를_반환한다")
    void 출처타입_필터가_null이면_전체를_반환한다() {
        Page<Policy> page = jpaRepository.findForAdminProcessing(
                "", "", null, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Policy::getId)
                .contains(policyAId, policyBId);
    }
}
