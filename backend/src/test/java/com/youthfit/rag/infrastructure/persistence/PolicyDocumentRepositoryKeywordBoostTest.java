package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.common.config.JpaAuditingConfig;
import com.youthfit.rag.domain.model.PolicyDocument;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
@DisplayName("PolicyDocumentRepository keyword boost")
class PolicyDocumentRepositoryKeywordBoostTest {

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
        // 컨테이너 빈 DB 라 ddl-auto: validate 로는 스키마가 없어서 실패. create-drop 으로 override.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    private static final int EMBEDDING_DIM = 1536;

    @Autowired
    private PolicyDocumentJpaRepository jpaRepository;

    private Long policyId;

    @BeforeEach
    void setUp() {
        policyId = 9001L;
        jpaRepository.deleteByPolicyId(policyId);
        // chunk 0: query 와 가까움 + 키워드 2개 hit
        // chunk 1: query 와 매우 가까움 + 키워드 hit 없음
        // chunk 2: query 와 멂 + 키워드 hit 없음
        savePolicyDocument(policyId, 0, "표 항목: 디딤씨앗통장 중복 가능 통장 리스트",
                twoAxisVector(0.4f, 0.6f));
        savePolicyDocument(policyId, 1, "기타 안내 — 별도 항목",
                twoAxisVector(0.5f, 0.7f));
        savePolicyDocument(policyId, 2, "그 외 일반 안내",
                twoAxisVector(0.0f, 1.0f));
    }

    @Test
    @DisplayName("keywords 빈 배열이면 순수 cosine distance 순서")
    void emptyKeywords_pureDistanceOrder() {
        float[] query = twoAxisVector(0.6f, 0.8f);

        List<Object[]> rows = jpaRepository.findSimilarByEmbedding(
                policyId, toVectorLiteral(query), new String[0], 10);

        assertThat(rows).hasSize(3);
        double d0 = ((Number) rows.get(0)[7]).doubleValue();
        double d1 = ((Number) rows.get(1)[7]).doubleValue();
        double d2 = ((Number) rows.get(2)[7]).doubleValue();
        assertThat(d0).isLessThanOrEqualTo(d1);
        assertThat(d1).isLessThanOrEqualTo(d2);
        assertThat((Integer) rows.get(2)[2]).isEqualTo(2);
    }

    @Test
    @DisplayName("키워드 hit 청크가 boost 적용으로 distance 가 줄어든다")
    void keywordsBoost_reducesHitChunkDistance() {
        float[] query = twoAxisVector(0.6f, 0.8f);
        String[] keywords = new String[]{"디딤씨앗통장", "중복"};

        List<Object[]> baseRows = jpaRepository.findSimilarByEmbedding(
                policyId, toVectorLiteral(query), new String[0], 10);
        double baseDistanceChunk0 = baseRows.stream()
                .filter(r -> ((Integer) r[2]) == 0)
                .map(r -> ((Number) r[7]).doubleValue())
                .findFirst().orElseThrow();

        List<Object[]> boostedRows = jpaRepository.findSimilarByEmbedding(
                policyId, toVectorLiteral(query), keywords, 10);
        double boostedDistanceChunk0 = boostedRows.stream()
                .filter(r -> ((Integer) r[2]) == 0)
                .map(r -> ((Number) r[7]).doubleValue())
                .findFirst().orElseThrow();

        assertThat(boostedDistanceChunk0)
                .isCloseTo(baseDistanceChunk0 * 0.85, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("키워드 hit 청크가 boost 후 더 가까운 청크보다 ranking 상위로 이동한다")
    void keywordsBoost_movesHitChunkAboveCloserChunk() {
        Long shiftPolicyId = 9002L;
        jpaRepository.deleteByPolicyId(shiftPolicyId);

        float[] query = twoAxisVector(1.0f, 0.0f);

        savePolicyDocument(shiftPolicyId, 0,
                "표 항목: 디딤씨앗통장 청년내일채움공제 꿈나래통장 중복 가능 리스트",
                twoAxisVector(0.6f, 0.8f));
        savePolicyDocument(shiftPolicyId, 1,
                "기타 일반 안내문",
                twoAxisVector(0.65f, 0.7599f));

        String[] keywords = new String[]{"디딤씨앗통장", "청년내일채움공제", "꿈나래통장"};

        List<Object[]> baseRows = jpaRepository.findSimilarByEmbedding(
                shiftPolicyId, toVectorLiteral(query), new String[0], 10);
        assertThat(baseRows).hasSize(2);
        assertThat((Integer) baseRows.get(0)[2]).isEqualTo(1);
        assertThat((Integer) baseRows.get(1)[2]).isEqualTo(0);

        List<Object[]> boostedRows = jpaRepository.findSimilarByEmbedding(
                shiftPolicyId, toVectorLiteral(query), keywords, 10);
        assertThat(boostedRows).hasSize(2);
        assertThat((Integer) boostedRows.get(0)[2]).isEqualTo(0);
        assertThat((Integer) boostedRows.get(1)[2]).isEqualTo(1);
    }

    private float[] twoAxisVector(float v0, float v1) {
        float[] v = new float[EMBEDDING_DIM];
        v[0] = v0;
        v[1] = v1;
        return v;
    }

    private void savePolicyDocument(Long policyId, int chunkIndex, String content, float[] embedding) {
        PolicyDocument doc = PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(chunkIndex)
                .content(content)
                .sourceHash("test-hash")
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(doc, "embedding", embedding);
        jpaRepository.save(doc);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
