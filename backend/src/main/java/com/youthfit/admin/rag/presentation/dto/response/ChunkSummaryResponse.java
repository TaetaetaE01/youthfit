package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.rag.application.dto.result.SimilarChunkResult;

public record ChunkSummaryResponse(
        long chunkId,
        int chunkIndex,
        double distance,
        String preview
) {
    public static ChunkSummaryResponse from(SimilarChunkResult c) {
        String content = c.content();
        String preview = content == null ? ""
                : content.length() <= 500 ? content : content.substring(0, 500);
        return new ChunkSummaryResponse(c.chunkId(), c.chunkIndex(), c.distance(), preview);
    }
}
