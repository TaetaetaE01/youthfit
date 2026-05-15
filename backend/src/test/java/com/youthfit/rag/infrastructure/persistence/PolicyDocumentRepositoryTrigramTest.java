package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.common.config.JpaAuditingConfig;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.PolicyDocumentSource;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
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
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PolicyDocumentRepositoryImpl.class})
@Testcontainers
@Sql(scripts = "classpath:sql/2026-05-16-policy-document-trigram-index.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DisplayName("PolicyDocumentRepository trigram 검색")
class PolicyDocumentRepositoryTrigramTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void initExtensions(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS pg_trgm;");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private PolicyDocumentJpaRepository jpaRepository;

    @Autowired
    private PolicyDocumentRepository repository;

    private Long policyId;

    @BeforeEach
    void setUp() {
        policyId = 9101L;
        jpaRepository.deleteByPolicyId(policyId);
        jpaRepository.saveAll(List.of(
                document(0, "희망저축계좌Ⅰ 가입 요건은 기준 중위소득 40% 이하입니다"),
                document(1, "내일키움수익금 자활근로사업단 12일 이상 실근무"),
                document(2, "청년내일저축계좌 2026년부터 차상위 초과자 신규모집 중단"),
                document(3, "이 청크는 전혀 무관한 내용입니다 lorem ipsum")
        ));
    }

    @Test
    @DisplayName("정확 토큰 매칭 청크가 가장 높은 similarity 로 반환된다")
    void exactTokenMatch() {
        List<SimilarChunk> result = repository.findTopByTrigram(
                policyId, "희망저축계좌", 0.1, 10
        );

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).content()).contains("희망저축계좌Ⅰ");
    }

    @Test
    @DisplayName("threshold 미만 청크는 결과에서 제외된다")
    void thresholdFilters() {
        List<SimilarChunk> highThreshold = repository.findTopByTrigram(
                policyId, "희망저축계좌", 0.9, 10
        );
        List<SimilarChunk> lowThreshold = repository.findTopByTrigram(
                policyId, "희망저축계좌", 0.05, 10
        );

        assertThat(lowThreshold.size()).isGreaterThanOrEqualTo(highThreshold.size());
    }

    @Test
    @DisplayName("limit 으로 결과 수가 제한된다")
    void limitWorks() {
        List<SimilarChunk> result = repository.findTopByTrigram(
                policyId, "청크", 0.05, 2
        );

        assertThat(result).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("다른 policyId 청크는 검색되지 않는다")
    void scopedByPolicyId() {
        List<SimilarChunk> result = repository.findTopByTrigram(
                9999L, "희망저축계좌", 0.05, 10
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("반환된 SimilarChunk 의 distance 는 1.0 - similarity 로 변환된 값이다")
    void distanceFieldIsConvertedFromSimilarity() {
        List<SimilarChunk> result = repository.findTopByTrigram(
                policyId, "희망저축계좌", 0.0, 10  // threshold=0 으로 모두 통과
        );

        // 정확 매칭 청크: similarity 높음 → distance 낮음 (0 에 가까움)
        SimilarChunk topChunk = result.get(0);
        assertThat(topChunk.distance()).isBetween(0.0, 1.0);
        assertThat(topChunk.distance()).isLessThan(0.5);  // 정확 매칭 청크의 distance 는 0.5 미만
    }

    private PolicyDocument document(int chunkIndex, String content) {
        return PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(chunkIndex)
                .content(content)
                .sourceHash("test-hash")
                .source(PolicyDocumentSource.BODY)
                .build();
    }
}
