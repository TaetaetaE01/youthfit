package com.youthfit.admin.rag.application.service;

import com.youthfit.admin.rag.application.dto.command.HybridOverrideCommand;
import com.youthfit.admin.rag.application.dto.command.RagPreviewCommand;
import com.youthfit.admin.rag.application.dto.result.RagPreviewResult;
import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.MergedChunk;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("RagPreviewService")
@ExtendWith(MockitoExtension.class)
class RagPreviewServiceTest {

    @InjectMocks private RagPreviewService service;
    @Mock private RagSearchService ragSearchService;
    @Mock private EmbeddingProvider embeddingProvider;
    @Mock private RankChangeCalculator rankChangeCalculator;
    @Mock private RagPreviewRateLimiter rateLimiter;

    private RagSearchTrace traceWith(List<MergedChunk> merged) {
        return new RagSearchTrace(
                new EffectiveConfig(true, 20, 60, 0.10, true, 5),
                List.of(), List.of(), merged, List.of("주거"), 100L);
    }

    @Test
    @DisplayName("embedding 은 한 번만 호출되고 baseline/candidate 양쪽 trace 가 반환된다")
    void embedsOnce_returnsBothTraces() {
        given(rateLimiter.tryAcquire(42L)).willReturn(true);
        float[] emb = new float[]{0.1f};
        given(embeddingProvider.embed("주거")).willReturn(emb);

        RagSearchTrace baseline = traceWith(List.of(new MergedChunk(1L, 0, 0.1, 0.0, 1, "a")));
        RagSearchTrace candidate = traceWith(List.of(new MergedChunk(2L, 1, 0.2, 0.0, 1, "b")));
        given(ragSearchService.searchRelevantChunksWithTrace(any(), eq(emb), any()))
                .willReturn(baseline, candidate);
        given(rankChangeCalculator.compute(any(), any())).willReturn(List.of());

        RagPreviewCommand cmd = new RagPreviewCommand(42L, 1L, "주거",
                new HybridOverrideCommand(null, null, 30, null, null, null));

        RagPreviewResult result = service.preview(cmd);

        verify(embeddingProvider, times(1)).embed("주거");
        verify(ragSearchService, times(2)).searchRelevantChunksWithTrace(any(), eq(emb), any());
        assertThat(result.baseline().trace()).isSameAs(baseline);
        assertThat(result.candidate().trace()).isSameAs(candidate);
    }

    @Test
    @DisplayName("baseline 호출은 overrides=null, candidate 는 변환된 HybridSearchOverrides 로 호출된다")
    void passesNullForBaseline_andOverridesForCandidate() {
        given(rateLimiter.tryAcquire(42L)).willReturn(true);
        given(embeddingProvider.embed(any())).willReturn(new float[]{0.1f});
        given(ragSearchService.searchRelevantChunksWithTrace(any(), any(), any()))
                .willReturn(traceWith(List.of()), traceWith(List.of()));
        given(rankChangeCalculator.compute(any(), any())).willReturn(List.of());

        HybridOverrideCommand candidate = new HybridOverrideCommand(null, null, 30, null, null, 7);
        service.preview(new RagPreviewCommand(42L, 1L, "주거", candidate));

        // baseline → null
        verify(ragSearchService).searchRelevantChunksWithTrace(any(), any(), eq((HybridSearchOverrides) null));
        // candidate → 변환된 record
        verify(ragSearchService).searchRelevantChunksWithTrace(any(), any(),
                eq(new HybridSearchOverrides(null, null, 30, null, null, 7)));
    }

    @Test
    @DisplayName("rate limit 초과 시 RagPreviewRateLimitException 발생, search 호출 없음")
    void overRateLimit_throws() {
        given(rateLimiter.tryAcquire(42L)).willReturn(false);

        RagPreviewCommand cmd = new RagPreviewCommand(42L, 1L, "주거",
                new HybridOverrideCommand(null, null, null, null, null, null));

        assertThatThrownBy(() -> service.preview(cmd))
                .isInstanceOf(RagPreviewRateLimitException.class);
        verify(embeddingProvider, times(0)).embed(any());
    }
}
