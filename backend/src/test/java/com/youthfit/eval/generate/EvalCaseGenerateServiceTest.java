package com.youthfit.eval.generate;

import com.youthfit.eval.config.EvalProperties;
import com.youthfit.eval.dataset.EvalDataset;
import com.youthfit.eval.dataset.EvalDatasetLoader;
import com.youthfit.eval.dataset.EvalQuestionType;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("EvalCaseGenerateService")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvalCaseGenerateServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyDocumentRepository policyDocumentRepository;
    @Mock private EvalQuestionLlm evalQuestionLlm;

    @TempDir
    Path tempDir;

    private EvalCaseGenerateService service;

    private Policy policy(Long id, String title) {
        Policy p = mock(Policy.class);
        given(p.getId()).willReturn(id);
        given(p.getTitle()).willReturn(title);
        return p;
    }

    private PolicyDocument chunk(int index, String content) {
        PolicyDocument d = mock(PolicyDocument.class);
        given(d.getChunkIndex()).willReturn(index);
        given(d.getContent()).willReturn(content);
        return d;
    }

    @BeforeEach
    void setUp() {
        EvalProperties props = new EvalProperties(
                tempDir.resolve("evalset.json").toString(),
                tempDir.resolve("candidate.json").toString(),
                tempDir.resolve("cache").toString(),
                tempDir.resolve("reports").toString(),
                true,
                new EvalProperties.Generate("gpt-4o-mini", 1200, "test-key", 10));
        service = new EvalCaseGenerateService(policyRepository, policyDocumentRepository,
                evalQuestionLlm, props);

        Policy p = policy(1L, "청년 월세 지원");
        given(policyRepository.findAllByFilters(any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(policyRepository.findAllByFilters(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(com.youthfit.policy.domain.model.SourceType.YOUTH_CENTER), any()))
                .willReturn(new PageImpl<>(List.of(p)));
        given(policyRepository.findById(1L)).willReturn(Optional.of(p));
        List<PolicyDocument> chunks = List.of(chunk(0, "지원 대상: 만 19세~34세 청년. 월 20만원을 지원합니다."));
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L))
                .willReturn(chunks);
    }

    @Test
    @DisplayName("dry-run(confirm=false)은 LLM 을 호출하지 않는다")
    void dryRunDoesNotCallLlm() {
        Path result = service.generateCandidates(false, null);

        assertThat(result).isNull();
        verify(evalQuestionLlm, never()).generateQuestions(anyString(), anyList());
    }

    @Test
    @DisplayName("스니펫이 청크 원문에 없으면 후보에서 제외한다 (환각 방지)")
    void dropsHallucinatedSnippets() throws Exception {
        given(evalQuestionLlm.generateQuestions(anyString(), anyList())).willReturn(List.of(
                new GeneratedEvalQuestion("지원 금액은?", EvalQuestionType.KEYWORD, "월 20만원을 지원"),
                new GeneratedEvalQuestion("환각 질문?", EvalQuestionType.KEYWORD, "원문에 없는 문장")));

        Path candidatePath = service.generateCandidates(true, null);

        EvalDataset candidate = new EvalDatasetLoader().load(candidatePath);
        List<String> questions = candidate.cases().stream()
                .map(c -> c.question()).toList();
        assertThat(questions).contains("지원 금액은?");
        assertThat(questions).doesNotContain("환각 질문?");
    }

    @Test
    @DisplayName("정책마다 NEGATIVE 케이스 1건이 배정된다 (expectedSnippets 빈 배열)")
    void assignsNegativeCasePerPolicy() throws Exception {
        given(evalQuestionLlm.generateQuestions(anyString(), anyList())).willReturn(List.of());

        Path candidatePath = service.generateCandidates(true, null);

        EvalDataset candidate = new EvalDatasetLoader().load(candidatePath);
        List<com.youthfit.eval.dataset.EvalCase> negatives = candidate.cases().stream()
                .filter(c -> c.questionType() == EvalQuestionType.NEGATIVE)
                .toList();
        assertThat(negatives).hasSize(1);
        assertThat(negatives.get(0).expectedSnippets()).isEmpty();
    }
}
