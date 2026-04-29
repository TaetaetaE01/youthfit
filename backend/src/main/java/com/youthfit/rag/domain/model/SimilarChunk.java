package com.youthfit.rag.domain.model;

public record SimilarChunk(
        Long id,
        Long policyId,
        int chunkIndex,
        String content,
        Long attachmentId,
        Integer pageStart,
        Integer pageEnd,
        double distance
) {
}
