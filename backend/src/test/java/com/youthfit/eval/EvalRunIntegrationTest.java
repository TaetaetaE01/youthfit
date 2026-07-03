package com.youthfit.eval;

import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.eval.dataset.EvalQuestionType;
import com.youthfit.eval.run.CaseResult;
import com.youthfit.eval.run.CaseStatus;
import com.youthfit.eval.run.EvalScenario;
import com.youthfit.eval.run.QueryEmbeddingFileCache;
import com.youthfit.eval.run.RetrievalEvaluator;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.PolicyDocumentSource;
import com.youthfit.rag.infrastructure.persistence.PolicyDocumentJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * eval run 파이프라인(RetrievalEvaluator → RagSearchService → pgvector) end-to-end 스모크.
 *
 * <p>컨테이너·시딩 셋업은 {@code AdminRagPreviewIntegrationTest} 를 그대로 재사용한다.
 * {@code spring.main.web-application-type=none} (application-eval.yml) 이므로 MockMvc 는
 * 불필요 — RetrievalEvaluator 를 직접 호출한다.</p>
 *
 * <p>{@code @SpringBootTest} 는 컨텍스트 기동 시 {@link EvalRunner} (ApplicationRunner) 를
 * 실행하므로, 자동 실행·{@code System.exit} 를 막기 위해 반드시
 * {@code youthfit.eval.runner-enabled=false} 를 지정한다 (Task 8 가드).</p>
 */
@DisplayName("eval run 파이프라인 통합 스모크")
@SpringBootTest(properties = "youthfit.eval.runner-enabled=false")
@ActiveProfiles("eval")
@Testcontainers
class EvalRunIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // pgvector + pg_trgm 확장 활성화 — hybrid-on 시나리오의 trigram 검색 경로까지 검증
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS pg_trgm;");
        // 빈 컨테이너 DB 에 스키마 생성 (validate 는 스키마가 없어 실패함)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // 불필요한 외부 연결 방지
        registry.add("spring.mail.host", () -> "localhost");
    }

    /** OpenAI 임베딩 호출 차단 — 고정 더미 벡터 반환 */
    @MockitoBean
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private RetrievalEvaluator retrievalEvaluator;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyDocumentJpaRepository policyDocumentJpaRepository;

    @TempDir
    Path tempDir;

    private static final int EMBEDDING_DIM = 1536;

    private QueryEmbeddingFileCache cache;
    private EvalCase evalCase;

    @BeforeEach
    void setUp() {
        // 더미 임베딩 — 1536차원, 모든 값 0.1f (pgvector 연산 가능한 유효 벡터)
        float[] dummyEmbedding = new float[EMBEDDING_DIM];
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dummyEmbedding[i] = 0.1f;
        }
        given(embeddingProvider.embed(anyString())).willReturn(dummyEmbedding);

        // 정책 1건 저장
        Policy policy = Policy.builder()
                .title("청년 월세 지원")
                .body("본문")
                .category(Category.HOUSING)
                .build();
        Policy saved = policyRepository.save(policy);

        // PolicyDocument 청크 2건 저장 — 하나에 기대 스니펫 포함
        List<PolicyDocument> docs = List.of(
                document(saved.getId(), 0, "청년 월세 지원 정책 개요 및 신청 절차 안내", dummyEmbedding),
                document(saved.getId(), 1, "지원 대상: 대학 재학생은 신청 대상에서 제외됩니다.", dummyEmbedding)
        );
        policyDocumentJpaRepository.saveAll(docs);

        evalCase = new EvalCase("p1-q1", saved.getId(), "청년 월세 지원",
                "재학생도 되나요?", EvalQuestionType.KEYWORD,
                List.of("대학 재학생은 신청 대상에서 제외"), null);

        cache = new QueryEmbeddingFileCache(tempDir, "text-embedding-3-small");
    }

    @Test
    @DisplayName("시딩된 정책·청크로 baseline 케이스가 OK 판정된다")
    void evaluatesSeededCase() {
        CaseResult result = retrievalEvaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.firstRelevantRank()).isNotNull();
        assertThat(result.effective()).isNotNull();
    }

    @Test
    @DisplayName("hybrid-on 시나리오도 예외 없이 실행된다 (pg_trgm 경로)")
    void hybridScenarioRuns() {
        CaseResult result = retrievalEvaluator.evaluate(evalCase, EvalScenario.of("hybrid-on"), cache);

        // pg_trgm extension 을 컨테이너 init 에서 생성했으므로 trigram 경로가 정상 동작해 OK 가 기대값.
        // (extension 이 없더라도 RagSearchService 가 예외를 잡아 vector 결과로 폴백하므로 여전히 OK.)
        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.firstRelevantRank()).isNotNull();
        assertThat(result.effective()).isNotNull();
    }

    // ──────────────────────────── helpers ────────────────────────────

    private PolicyDocument document(long policyId, int chunkIndex, String content, float[] embedding) {
        PolicyDocument doc = PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(chunkIndex)
                .content(content)
                .sourceHash("test-hash-" + chunkIndex)
                .source(PolicyDocumentSource.BODY)
                .build();
        ReflectionTestUtils.setField(doc, "embedding", embedding);
        return doc;
    }
}
