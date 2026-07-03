package com.youthfit.eval;

import com.youthfit.eval.generate.EvalCaseGenerateService;
import com.youthfit.eval.reindex.EvalReindexService;
import com.youthfit.eval.run.RetrievalEvaluator;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.rag.infrastructure.external.OpenAiEmbeddingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("EvalRunner")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvalRunnerTest {

    @InjectMocks
    private EvalRunner runner;

    @Mock private EvalCaseGenerateService generateService;
    @Mock private RetrievalEvaluator retrievalEvaluator;
    @Mock private com.youthfit.eval.config.EvalProperties evalProperties;
    @Mock private QnaProperties qnaProperties;
    @Mock private EvalReindexService evalReindexService;
    @Mock private OpenAiEmbeddingProperties embeddingProperties;

    @Test
    @DisplayName("--eval.mode=generate 는 generate 서비스로 디스패치 (confirm 기본 false)")
    void dispatchesGenerate() throws Exception {
        runner.dispatch(new DefaultApplicationArguments("--eval.mode=generate"));

        verify(generateService).generateCandidates(false, null);
    }

    @Test
    @DisplayName("--eval.mode 누락은 명확한 예외")
    void failsOnMissingMode() {
        assertThatThrownBy(() -> runner.dispatch(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--eval.mode");
    }

    @Test
    @DisplayName("--eval.mode=reindex 는 confirm 없으면 dry-run — reindexPolicy 를 호출하지 않는다")
    void dispatchesReindexDryRun() throws Exception {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        given(p.getId()).willReturn(1L);
        given(p.getTitle()).willReturn("정책");
        List<Policy> targets = List.of(p);
        given(evalReindexService.findTargets(null)).willReturn(targets);

        runner.dispatch(new DefaultApplicationArguments("--eval.mode=reindex"));

        verify(evalReindexService, never()).reindexPolicy(anyLong());
    }

    @Test
    @DisplayName("--eval.mode=reindex --eval.confirm=true 는 대상마다 reindexPolicy 호출")
    void dispatchesReindexConfirmed() throws Exception {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        given(p.getId()).willReturn(1L);
        given(p.getTitle()).willReturn("정책");
        List<Policy> targets = List.of(p);
        given(evalReindexService.findTargets(null)).willReturn(targets);
        given(evalReindexService.reindexPolicy(1L)).willReturn(true);

        runner.dispatch(new DefaultApplicationArguments("--eval.mode=reindex", "--eval.confirm=true"));

        verify(evalReindexService).reindexPolicy(1L);
    }

    @Test
    @DisplayName("캐시 라벨은 실제 호출 모델 — evalset 라벨과 불일치해도 실제 모델 사용")
    void resolveCacheLabel_usesActualModel() {
        assertThat(EvalRunner.resolveCacheLabel("text-embedding-3-large", "text-embedding-3-small"))
                .isEqualTo("text-embedding-3-large");
        assertThat(EvalRunner.resolveCacheLabel("text-embedding-3-small", "text-embedding-3-small"))
                .isEqualTo("text-embedding-3-small");
    }
}
