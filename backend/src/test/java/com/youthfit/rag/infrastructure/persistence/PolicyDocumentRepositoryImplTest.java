package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.common.config.JpaAuditingConfig;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.PolicyDocumentSource;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PolicyDocumentRepositoryImpl.class})
@Testcontainers
@DisplayName("PolicyDocumentRepositoryImpl 첨부 임베딩 카운트")
class PolicyDocumentRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void initVectorExtension(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "CREATE EXTENSION IF NOT EXISTS vector");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private PolicyDocumentJpaRepository jpaRepository;

    @Autowired
    private PolicyDocumentRepository repository;

    @Test
    @DisplayName("countAttachmentEmbeddingsByPolicyIds — 정책별 distinct attachmentId 개수 반환")
    void countAttachmentEmbeddingsByPolicyIds_countsDistinctAttachmentIds() {
        persistDocument(100L, PolicyDocumentSource.BODY, null, 0);
        persistDocument(100L, PolicyDocumentSource.ATTACHMENT, 401L, 0);
        persistDocument(100L, PolicyDocumentSource.ATTACHMENT, 401L, 1); // 같은 attachment 의 다른 chunk
        persistDocument(100L, PolicyDocumentSource.ATTACHMENT, 402L, 0);
        persistDocument(101L, PolicyDocumentSource.ATTACHMENT, 501L, 0);

        Map<Long, Long> result = repository.countAttachmentEmbeddingsByPolicyIds(List.of(100L, 101L));

        assertThat(result).containsEntry(100L, 2L);
        assertThat(result).containsEntry(101L, 1L);
    }

    @Test
    @DisplayName("countAttachmentEmbeddingsByPolicyIds — 첨부 임베딩이 없는 정책은 결과 맵에서 누락")
    void countAttachmentEmbeddingsByPolicyIds_omitsPolicyWithoutAttachmentDocs() {
        persistDocument(200L, PolicyDocumentSource.BODY, null, 0);
        persistDocument(200L, PolicyDocumentSource.ENRICHMENT_BODY, null, 1);

        Map<Long, Long> result = repository.countAttachmentEmbeddingsByPolicyIds(List.of(200L));

        assertThat(result).doesNotContainKey(200L);
    }

    @Test
    @DisplayName("countAttachmentEmbeddingsByPolicyIds — 빈 리스트는 빈 맵을 반환")
    void countAttachmentEmbeddingsByPolicyIds_emptyInput_returnsEmptyMap() {
        Map<Long, Long> result = repository.countAttachmentEmbeddingsByPolicyIds(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findEmbeddedAttachmentIds — 특정 정책의 임베딩된 attachmentId set 반환")
    void findEmbeddedAttachmentIds_returnsDistinctAttachmentIds() {
        persistDocument(300L, PolicyDocumentSource.BODY, null, 0);
        persistDocument(300L, PolicyDocumentSource.ATTACHMENT, 601L, 0);
        persistDocument(300L, PolicyDocumentSource.ATTACHMENT, 601L, 1);
        persistDocument(300L, PolicyDocumentSource.ATTACHMENT, 602L, 0);

        Set<Long> result = repository.findEmbeddedAttachmentIds(300L);

        assertThat(result).containsExactlyInAnyOrder(601L, 602L);
    }

    @Test
    @DisplayName("findEmbeddedAttachmentIds — 첨부 임베딩이 없으면 빈 set 반환")
    void findEmbeddedAttachmentIds_noAttachmentDocs_returnsEmptySet() {
        persistDocument(400L, PolicyDocumentSource.BODY, null, 0);

        Set<Long> result = repository.findEmbeddedAttachmentIds(400L);

        assertThat(result).isEmpty();
    }

    private void persistDocument(Long policyId, PolicyDocumentSource source, Long attachmentId, int chunkIndex) {
        PolicyDocument doc = PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(chunkIndex)
                .content("test content " + policyId + "-" + chunkIndex)
                .sourceHash("test-hash-" + policyId + "-" + chunkIndex)
                .attachmentId(attachmentId)
                .source(source)
                .build();
        jpaRepository.save(doc);
    }
}
