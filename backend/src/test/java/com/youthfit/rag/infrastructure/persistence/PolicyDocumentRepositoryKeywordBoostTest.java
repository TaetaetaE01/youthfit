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
        // 거리 설계 (query = [0.6, 0.8] 단위벡터):
        //   chunk 0: [1, 0]  → cosine = 0.6  → distance ≈ 0.4
        //   chunk 1: [0.5, 0.7] (query 와 거의 동일 방향, 살짝 떨어짐) → distance ≈ 0.0014
        //   chunk 2: [0, 1]  → cosine = 0.8  → distance ≈ 0.2
        // 키워드 boost 시:
        //   chunk 0: 두 키워드 hit → 0.4 * 0.85 = 0.34
        //   chunk 1: 키워드 hit 없음 → 0.0014
        //   chunk 2: 키워드 hit 없음 → 0.2
        // 키워드 boost 만으로는 chunk 1 이 1위 유지 → 키워드 boost 가 충분히 클 때만 순위 변경.
        // 본 테스트는 chunk 1 의 거리를 chunk 0 의 boost 후 거리(0.34) 보다 크게 만들어야 함.
        //   chunk 1: [0.6, 0.4] → cosine = (0.36+0.32)/(sqrt(0.52)*1) = 0.68/0.721 ≈ 0.943 → distance ≈ 0.057
        //   chunk 0 boost: 0.4 * 0.85 = 0.34 → chunk 1 (0.057) 이 여전히 1위.
        // 따라서 chunk 0 의 거리를 더 작게, 그리고 chunk 1 거리를 더 크게 조정:
        //   chunk 0: query 와 비슷하게 [0.6, 0.7], distance 작음 (≈0.005), 키워드 2개 hit
        //   chunk 1: query 와 멀리 [0.2, 0.9], distance 큼, 키워드 1개 hit
        //   chunk 2: 중간 [0.5, 0.5], 키워드 0개 hit
        // boost 미적용: chunk 0 (~0.005) < chunk 2 < chunk 1
        // boost 적용: chunk 0 0.005*0.85 < chunk 1 (큼)*0.92 → 여전히 chunk 0 1위 → 의미 없음.
        //
        // 단순히 boost 검증을 위해 의도적으로:
        //   chunk 0 의 원본 distance 를 약간 더 큼 (chunk 1 보다 멀지만, boost 후 chunk 1 보다 작아짐)
        savePolicyDocument(policyId, 0, "표 항목: 디딤씨앗통장 중복 가능 통장 리스트",
                twoAxisVector(0, 1, 0.4f, 0.6f));   // distance ≈ 1 - (0.6*0.4+0.8*0.6)/sqrt(0.52) ≈ 1 - 0.72/0.721 ≈ 0.001
        savePolicyDocument(policyId, 1, "기타 안내 — 별도 항목",
                twoAxisVector(0, 1, 0.5f, 0.7f));   // distance 작음
        savePolicyDocument(policyId, 2, "그 외 일반 안내",
                twoAxisVector(0, 1, 0.0f, 1.0f));   // distance ≈ 0.2
    }

    @Test
    @DisplayName("keywords 빈 배열이면 순수 cosine distance 순서")
    void emptyKeywords_pureDistanceOrder() {
        float[] query = twoAxisVector(0, 1, 0.6f, 0.8f);

        List<Object[]> rows = jpaRepository.findSimilarByEmbedding(
                policyId, toVectorLiteral(query), new String[0], 10);

        assertThat(rows).hasSize(3);
        // 마지막 컬럼이 distance — 오름차순 정렬 검증
        double d0 = ((Number) rows.get(0)[7]).doubleValue();
        double d1 = ((Number) rows.get(1)[7]).doubleValue();
        double d2 = ((Number) rows.get(2)[7]).doubleValue();
        assertThat(d0).isLessThanOrEqualTo(d1);
        assertThat(d1).isLessThanOrEqualTo(d2);
        // chunk 2 ([0,1]) 가 가장 멂 — 마지막
        assertThat((Integer) rows.get(2)[2]).isEqualTo(2);
    }

    @Test
    @DisplayName("키워드 hit 청크가 boost 적용으로 distance 가 줄어든다")
    void keywordsBoost_reducesHitChunkDistance() {
        float[] query = twoAxisVector(0, 1, 0.6f, 0.8f);
        String[] keywords = new String[]{"디딤씨앗통장", "중복"};

        // boost 미적용 시 chunk 0 distance
        List<Object[]> baseRows = jpaRepository.findSimilarByEmbedding(
                policyId, toVectorLiteral(query), new String[0], 10);
        double baseDistanceChunk0 = baseRows.stream()
                .filter(r -> ((Integer) r[2]) == 0)
                .map(r -> ((Number) r[7]).doubleValue())
                .findFirst().orElseThrow();

        // boost 적용 시 chunk 0 distance
        List<Object[]> boostedRows = jpaRepository.findSimilarByEmbedding(
                policyId, toVectorLiteral(query), keywords, 10);
        double boostedDistanceChunk0 = boostedRows.stream()
                .filter(r -> ((Integer) r[2]) == 0)
                .map(r -> ((Number) r[7]).doubleValue())
                .findFirst().orElseThrow();

        // chunk 0 은 키워드 2 개 hit → distance × 0.85
        assertThat(boostedDistanceChunk0)
                .isCloseTo(baseDistanceChunk0 * 0.85, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("키워드 hit 청크가 boost 후 더 가까운 청크보다 ranking 상위로 이동한다")
    void keywordsBoost_movesHitChunkAboveCloserChunk() {
        Long shiftPolicyId = 9002L;
        jpaRepository.deleteByPolicyId(shiftPolicyId);

        // query = [1, 0]
        float[] query = twoAxisVector(0, 1, 1.0f, 0.0f);

        // chunk 0: cosine_sim = 0.6, distance ≈ 0.4. 키워드 3개 hit → distance × 0.78 ≈ 0.312
        savePolicyDocument(shiftPolicyId, 0,
                "표 항목: 디딤씨앗통장 청년내일채움공제 꿈나래통장 중복 가능 리스트",
                twoAxisVector(0, 1, 0.6f, 0.8f));
        // chunk 1: cosine_sim = 0.65, distance ≈ 0.35. 키워드 0 hit → boost 없음
        savePolicyDocument(shiftPolicyId, 1,
                "기타 일반 안내문",
                twoAxisVector(0, 1, 0.65f, 0.7599f));

        String[] keywords = new String[]{"디딤씨앗통장", "청년내일채움공제", "꿈나래통장"};

        // boost 미적용: chunk 1 (0.35) 이 chunk 0 (0.4) 보다 가까움 → 1위 chunk 1
        List<Object[]> baseRows = jpaRepository.findSimilarByEmbedding(
                shiftPolicyId, toVectorLiteral(query), new String[0], 10);
        assertThat(baseRows).hasSize(2);
        assertThat((Integer) baseRows.get(0)[2]).isEqualTo(1);
        assertThat((Integer) baseRows.get(1)[2]).isEqualTo(0);

        // boost 적용: chunk 0 (0.312) 이 chunk 1 (0.35) 보다 가까워짐 → ranking 역전, 1위 chunk 0
        List<Object[]> boostedRows = jpaRepository.findSimilarByEmbedding(
                shiftPolicyId, toVectorLiteral(query), keywords, 10);
        assertThat(boostedRows).hasSize(2);
        assertThat((Integer) boostedRows.get(0)[2]).isEqualTo(0);
        assertThat((Integer) boostedRows.get(1)[2]).isEqualTo(1);
    }

    private float[] axisVector(int axis, float value) {
        float[] v = new float[EMBEDDING_DIM];
        v[axis] = value;
        return v;
    }

    private float[] twoAxisVector(int a1, int a2, float v1, float v2) {
        float[] v = new float[EMBEDDING_DIM];
        v[a1] = v1;
        v[a2] = v2;
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
