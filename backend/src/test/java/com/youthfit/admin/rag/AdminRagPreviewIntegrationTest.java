package com.youthfit.admin.rag;

import com.youthfit.admin.rag.application.service.RagPreviewRateLimiter;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.PolicyDocumentSource;
import com.youthfit.rag.infrastructure.persistence.PolicyDocumentJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller → Service → Repository → pgvector 까지의 end-to-end wire 검증.
 *
 * <p>Redis 의존(RagPreviewRateLimiter), OpenAI 의존(EmbeddingProvider) 은
 * @MockitoBean 으로 격리하여 외부 인프라 없이 실행 가능하다.</p>
 *
 * <p>PostgreSQL(pgvector) 은 testcontainers 로 실제 컨테이너를 띄워 검증한다.
 * Redis testcontainers 모듈이 build.gradle 에 포함되지 않으므로
 * RagPreviewRateLimiter 는 mock 으로 대체한다.</p>
 */
@DisplayName("AdminRagPreview 통합 (end-to-end wire)")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminRagPreviewIntegrationTest {

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
        // 빈 컨테이너 DB 에 스키마 생성 (validate 는 스키마가 없어 실패함)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // hybrid 검색 활성화 — baseline rrfK=60, candidate rrfK=30 비교를 위해 필요
        registry.add("rag.hybrid.enabled", () -> "true");
        registry.add("rag.hybrid.rrf-k", () -> "60");
        registry.add("rag.hybrid.top-n-per-search", () -> "20");
        registry.add("rag.hybrid.trigram-threshold", () -> "0.0");
        // Redis 연결 없이 기동되도록 실제 Redis 주소 설정 (RateLimiter mock 으로 우회)
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
        // 불필요한 외부 연결 방지
        registry.add("spring.mail.host", () -> "localhost");
    }

    /** OpenAI 임베딩 호출 차단 — 고정 더미 벡터 반환 */
    @MockitoBean
    private EmbeddingProvider embeddingProvider;

    /** Redis 의존 차단 — rate limit 항상 통과 */
    @MockitoBean
    private RagPreviewRateLimiter ragPreviewRateLimiter;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyDocumentJpaRepository policyDocumentJpaRepository;

    private static final int EMBEDDING_DIM = 1536;
    private static final long SEEDED_POLICY_ID = 77001L;

    @BeforeEach
    void setUp() {
        // 더미 임베딩 — 1536차원, 모든 값 0.1f (pgvector 연산 가능한 유효 벡터)
        float[] dummyEmbedding = new float[EMBEDDING_DIM];
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dummyEmbedding[i] = 0.1f;
        }

        given(embeddingProvider.embed(anyString())).willReturn(dummyEmbedding);
        given(ragPreviewRateLimiter.tryAcquire(1L)).willReturn(true);

        // 이전 실행 잔여 데이터 정리
        policyDocumentJpaRepository.deleteByPolicyId(SEEDED_POLICY_ID);

        // 정책 청크 5개 시드 — 내용에 '주거' 포함하여 trigram 매칭 유도
        List<PolicyDocument> docs = List.of(
                document(0, "주거 지원 정책 개요 — 청년 임차보증금 대출 지원", dummyEmbedding),
                document(1, "주거 안정 월세 지원 신청 요건 및 절차 안내", dummyEmbedding),
                document(2, "청년 주거급여 분리지급 대상 및 지원 금액", dummyEmbedding),
                document(3, "전세사기 피해자 주거 지원 긴급 대책", dummyEmbedding),
                document(4, "기숙사형 청년 주택 입주 신청 방법", dummyEmbedding)
        );
        policyDocumentJpaRepository.saveAll(docs);
    }

    @Test
    @DisplayName("baseline(rrfK=60) vs candidate(rrfK=30) 비교 호출 시 양쪽 결과 배열 반환")
    @WithMockUser(roles = "ADMIN", username = "1")
    void baselineVsCandidate_returnsBothSidesAndDiff() throws Exception {
        String body = """
                {"policyId": %d, "query": "주거", "candidate": {"rrfK": 30}}
                """.formatted(SEEDED_POLICY_ID);

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseline.config.rrfK").value(60))
                .andExpect(jsonPath("$.data.candidate.config.rrfK").value(30))
                .andExpect(jsonPath("$.data.baseline.merged").isArray())
                .andExpect(jsonPath("$.data.candidate.merged").isArray());
    }

    // ──────────────────────────── helpers ────────────────────────────

    private PolicyDocument document(int chunkIndex, String content, float[] embedding) {
        PolicyDocument doc = PolicyDocument.builder()
                .policyId(SEEDED_POLICY_ID)
                .chunkIndex(chunkIndex)
                .content(content)
                .sourceHash("test-hash-" + chunkIndex)
                .source(PolicyDocumentSource.BODY)
                .build();
        ReflectionTestUtils.setField(doc, "embedding", embedding);
        return doc;
    }
}
