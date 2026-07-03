package com.youthfit.eval;

import com.youthfit.eval.generate.EvalCaseGenerateService;
import com.youthfit.eval.run.RetrievalEvaluator;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
