package com.youthfit.guide.application.service;

import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.ChunkInput;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.GuideContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MapReduceGuideOrchestratorTest {

    @Mock private GuideLlmProvider llmProvider;

    private MapReduceGuideOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        TokenCounter tokenCounter = new TokenCounter();
        // budget 100 tokens 로 작게 잡아 분할이 쉽게 일어나게 함
        orchestrator = new MapReduceGuideOrchestrator(llmProvider, tokenCounter,
                "gpt-4o-mini", 100);
    }

    @Test
    @DisplayName("청크 합이 budget 을 넘으면 여러 그룹으로 분할되고 partial 가 N 번 호출된다")
    void splitsByBudgetAndCallsPartialMultipleTimes() {
        ChunkInput c1 = new ChunkInput("가".repeat(100), null, null, null, "BODY");
        ChunkInput c2 = new ChunkInput("나".repeat(100), null, null, null, "BODY");
        ChunkInput c3 = new ChunkInput("다".repeat(100), null, null, null, "BODY");
        GuideGenerationInput input = buildInput(List.of(c1, c2, c3));

        GuideContent dummy = dummyContent();
        given(llmProvider.generatePartialGuide(any())).willReturn(dummy);
        given(llmProvider.mergePartialGuides(any(), any())).willReturn(dummy);

        GuideContent result = orchestrator.generate(input);

        assertThat(result).isEqualTo(dummy);
        // 적어도 2번 호출 (정확한 수는 토큰 측정에 따라 다를 수 있으나 2+ 보장)
        verify(llmProvider, org.mockito.Mockito.atLeast(2)).generatePartialGuide(any());
        verify(llmProvider, times(1)).mergePartialGuides(any(), any());
    }

    @Test
    @DisplayName("partial 일부 실패 시 성공한 것만으로 merge 진행")
    void partialFailureFallsThroughToMerge() {
        ChunkInput c1 = new ChunkInput("가".repeat(100), null, null, null, "BODY");
        ChunkInput c2 = new ChunkInput("나".repeat(100), null, null, null, "BODY");
        GuideGenerationInput input = buildInput(List.of(c1, c2));

        GuideContent ok = dummyContent();
        given(llmProvider.generatePartialGuide(any()))
                .willReturn(ok)
                .willThrow(new RuntimeException("rate limit"));
        given(llmProvider.mergePartialGuides(any(), any())).willReturn(ok);

        GuideContent result = orchestrator.generate(input);

        assertThat(result).isEqualTo(ok);
        // partial 은 적어도 2번 호출 시도. 성공한 partial 만으로 merge.
        verify(llmProvider, org.mockito.Mockito.atLeast(2)).generatePartialGuide(any());
        verify(llmProvider, times(1)).mergePartialGuides(any(),
                argThat(list -> list.size() >= 1 && list.size() < 2));
    }

    @Test
    @DisplayName("partial 전부 실패 시 RuntimeException 류를 던지고 merge 미호출")
    void allPartialFailureThrows() {
        ChunkInput c1 = new ChunkInput("가".repeat(100), null, null, null, "BODY");
        GuideGenerationInput input = buildInput(List.of(c1));

        given(llmProvider.generatePartialGuide(any()))
                .willThrow(new RuntimeException("rate limit"));

        assertThatThrownBy(() -> orchestrator.generate(input))
                .isInstanceOf(RuntimeException.class);
        verify(llmProvider, times(0)).mergePartialGuides(any(), any());
    }

    private GuideGenerationInput buildInput(List<ChunkInput> chunks) {
        return new GuideGenerationInput(
                1L, "title", 2025, "summary", "body",
                null, null, null, null, null, null, chunks, null);
    }

    private GuideContent dummyContent() {
        // GuideContent(String oneLineSummary, List<GuideHighlight> highlights,
        //   GuidePairedSection target, GuidePairedSection criteria,
        //   GuidePairedSection content, GuideListSection applyMethod,
        //   GuideListSection deadlineNote, GuideListSection requiredDocuments,
        //   GuideListSection contact, List<GuidePitfall> pitfalls)
        return new GuideContent(
                "요약",
                List.of(),  // highlights
                null, null, null,
                null, null, null, null,
                List.of()   // pitfalls
        );
    }
}
