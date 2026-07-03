package com.youthfit.rag.application.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.PolicyDocumentSource;
import com.youthfit.rag.infrastructure.persistence.PolicyDocumentJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * #173 회귀 — pg_trgm extension 이 없는 DB 에서 hybrid 검색이 trigram 실패를
 * vector 결과로 정상 폴백하는지 검증한다.
 *
 * <p>{@code PolicyDocumentRepositoryTrigramTest} 와 달리 이 테스트는 컨테이너에
 * {@code CREATE EXTENSION vector} 만 실행하고 {@code pg_trgm} 은 의도적으로 설치하지
 * 않는다. {@code findTopByTrigram} 이 REQUIRES_NEW 로 격리되지 않으면 트랜잭션이
 * aborted 상태가 되어 catch 폴백이 무효화되고 검색 호출 전체가 예외로 실패한다.</p>
 */
@DisplayName("RagSearchService trigram 실패 → vector 폴백 (#173)")
@SpringBootTest(properties = "rag.hybrid.enabled=true")
@Testcontainers
class RagSearchTrigramFallbackIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // pgvector 만 설치하고 pg_trgm 은 설치하지 않는다 — findTopByTrigram 이
        // "function similarity(text, text) does not exist" 로 실패하는 상황을 재현한다.
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "CREATE EXTENSION IF NOT EXISTS vector;");
        // 빈 컨테이너 DB 에 스키마 생성 (validate 는 스키마가 없어 실패함)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Redis 가 실행 중이 아니어도 컨텍스트 부팅이 가능하도록 dummy 주소 지정
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
        // 불필요한 외부 연결 방지
        registry.add("spring.mail.host", () -> "localhost");
    }

    /** OpenAI 임베딩 호출 차단 — 고정 더미 벡터 반환 */
    @MockitoBean
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private RagSearchService ragSearchService;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyDocumentJpaRepository policyDocumentJpaRepository;

    private static final int EMBEDDING_DIM = 1536;

    private Long policyId;
    private float[] dummyEmbedding;

    @BeforeEach
    void setUp() {
        // 더미 임베딩 — 1536차원, 모든 값 0.1f (pgvector 연산 가능한 유효 벡터)
        dummyEmbedding = new float[EMBEDDING_DIM];
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dummyEmbedding[i] = 0.1f;
        }
        given(embeddingProvider.embed(anyString())).willReturn(dummyEmbedding);

        Policy policy = Policy.builder()
                .title("청년 월세 지원")
                .body("본문")
                .category(Category.HOUSING)
                .build();
        Policy saved = policyRepository.save(policy);
        policyId = saved.getId();

        policyDocumentJpaRepository.saveAll(List.of(
                document(policyId, 0, "청년 월세 지원 정책 개요 및 신청 절차 안내"),
                document(policyId, 1, "지원 대상: 대학 재학생은 신청 대상에서 제외됩니다.")
        ));
    }

    @Test
    @DisplayName("searchRelevantChunksWithTrace 는 trigram 실패에도 예외 없이 vector 결과로 폴백한다")
    void withTraceFallsBackToVectorOnTrigramFailure() {
        SearchChunksCommand command = new SearchChunksCommand(policyId, "재학생도 되나요?");
        HybridSearchOverrides overrides = new HybridSearchOverrides(true, null, null, null, null, null);

        RagSearchTrace trace = ragSearchService.searchRelevantChunksWithTrace(command, dummyEmbedding, overrides);

        assertThat(trace.trigramTopN()).isEmpty();
        assertThat(trace.merged()).isNotEmpty();
    }

    @Test
    @DisplayName("searchRelevantChunks 리스트 반환 경로도 rag.hybrid.enabled=true 에서 예외 없이 결과를 반환한다")
    void listReturningPathFallsBackWithoutException() {
        SearchChunksCommand command = new SearchChunksCommand(policyId, "재학생도 되나요?");

        List<PolicyDocumentChunkResult> results =
                ragSearchService.searchRelevantChunks(command, dummyEmbedding);

        assertThat(results).isNotEmpty();
    }

    // ──────────────────────────── helpers ────────────────────────────

    private PolicyDocument document(long policyId, int chunkIndex, String content) {
        PolicyDocument doc = PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(chunkIndex)
                .content(content)
                .sourceHash("test-hash-" + chunkIndex)
                .source(PolicyDocumentSource.BODY)
                .build();
        ReflectionTestUtils.setField(doc, "embedding", dummyEmbedding);
        return doc;
    }
}
