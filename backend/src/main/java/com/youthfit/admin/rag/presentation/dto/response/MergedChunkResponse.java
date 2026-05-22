package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.rag.application.dto.result.MergedChunk;

public record MergedChunkResponse(
        long chunkId,
        int chunkIndex,
        double distance,
        double rrfScore,
        int rank,
        String preview
) {
    public static MergedChunkResponse from(MergedChunk c) {
        return new MergedChunkResponse(
                c.chunkId(), c.chunkIndex(), c.distance(), c.rrfScore(), c.rank(), c.preview());
    }
}
