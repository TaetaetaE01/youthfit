package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.eval.dataset.EvalQuestionType;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.MergedChunk;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@DisplayName("RetrievalEvaluator")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetrievalEvaluatorTest {

    @InjectMocks
    private RetrievalEvaluator evaluator;

    @Mock private RagSearchService ragSearchService;
    @Mock private EmbeddingProvider embeddingProvider;
    @Mock private QueryRewriter queryRewriter;
    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyDocumentRepository policyDocumentRepository;

    @TempDir
    Path tempDir;

    private QueryEmbeddingFileCache cache;
    private final EvalCase evalCase = new EvalCase("p1-q1", 1L, "청년 월세 지원",
            "재학생도 되나요?", EvalQuestionType.KEYWORD,
            List.of("대학 재학생은 신청 대상에서 제외"), null);

    private Policy policy(String title) {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        given(p.getTitle()).willReturn(title);
        return p;
    }

    private RagSearchTrace trace(List<SimilarChunk> vec, List<MergedChunk> merged) {
        EffectiveConfig effective = new EffectiveConfig(false, 20, 60, 0.1, true, 5);
        return new RagSearchTrace(effective, vec, List.of(), merged, List.of(), 42L);
    }

    @BeforeEach
    void setUp() {
        cache = new QueryEmbeddingFileCache(tempDir, "test-model");
        given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
        Policy defaultPolicy = policy("청년 월세 지원");
        given(policyRepository.findById(1L)).willReturn(Optional.of(defaultPolicy));
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L))
                .willReturn(List.of(org.mockito.Mockito.mock(PolicyDocument.class)));
    }

    @Test
    @DisplayName("스니펫 포함 청크를 정답으로 마킹하고 첫 정답 순위를 계산한다")
    void marksRelevantChunks() {
        SimilarChunk c1 = new SimilarChunk(11L, 1L, 0, "보증금 관련 내용", null, null, null, 0.5);
        SimilarChunk c2 = new SimilarChunk(12L, 1L, 1,
                "지원 대상: 대학 재학생은 신청 대상에서 제외됩니다.", null, null, null, 0.6);
        List<MergedChunk> merged = List.of(
                new MergedChunk(11L, 0, 0.5, 0.0, 1, "보증금"),
                new MergedChunk(12L, 1, 0.6, 0.0, 2, "지원 대상"));
        given(ragSearchService.searchRelevantChunksWithTrace(any(), any(), any()))
                .willReturn(trace(List.of(c1, c2), merged));

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.firstRelevantRank()).isEqualTo(2);
        assertThat(result.ranked()).extracting(RankedChunk::relevant)
                .containsExactly(false, true);
        assertThat(result.tookMs()).isEqualTo(42L);
    }

    @Test
    @DisplayName("정책 title 불일치는 STALE")
    void detectsStaleCase() {
        Policy otherPolicy = policy("전혀 다른 정책");
        given(policyRepository.findById(1L)).willReturn(Optional.of(otherPolicy));

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.STALE);
    }

    @Test
    @DisplayName("청크 0건이면 NO_CHUNKS")
    void detectsNoChunks() {
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(List.of());

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.NO_CHUNKS);
    }

    @Test
    @DisplayName("rewrite-on 시나리오는 재작성 질문으로 검색하고 effectiveQuestion 에 기록")
    void appliesQueryRewrite() {
        given(queryRewriter.rewrite("청년 월세 지원", "재학생도 되나요?"))
                .willReturn(Optional.of("청년 월세 지원 대학 재학생 신청 자격"));
        given(ragSearchService.searchRelevantChunksWithTrace(any(), any(), any()))
                .willReturn(trace(List.of(), List.of()));

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("rewrite-on"), cache);

        assertThat(result.effectiveQuestion()).isEqualTo("청년 월세 지원 대학 재학생 신청 자격");
    }

    @Test
    @DisplayName("임베딩 예외는 SKIPPED")
    void skipsOnEmbeddingFailure() {
        given(embeddingProvider.embed(anyString())).willThrow(new RuntimeException("API 오류"));

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.SKIPPED);
    }
}
