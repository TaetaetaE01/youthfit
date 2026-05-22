package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.admin.rag.application.dto.result.PreviewSideResult;

import java.util.List;

public record PreviewSideResponse(
        EffectiveConfigResponse config,
        List<ChunkSummaryResponse> vectorTopN,
        List<ChunkSummaryResponse> trigramTopN,
        List<MergedChunkResponse> merged,
        long tookMs
) {
    public static PreviewSideResponse from(PreviewSideResult side) {
        return new PreviewSideResponse(
                EffectiveConfigResponse.from(side.trace().effective()),
                side.trace().vectorTopN().stream().map(ChunkSummaryResponse::from).toList(),
                side.trace().trigramTopN().stream().map(ChunkSummaryResponse::from).toList(),
                side.trace().merged().stream().map(MergedChunkResponse::from).toList(),
                side.trace().tookMs());
    }
}
