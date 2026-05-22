package com.youthfit.rag.application.dto.result;

import com.youthfit.rag.domain.model.SimilarChunk;

/**
 * application-layer projection of SimilarChunk.
 * Breaks the presentation→domain dependency: presentation DTOs use this type
 * instead of importing the domain model directly.
 */
public record SimilarChunkResult(long chunkId, int chunkIndex, double distance, String content) {
    public static SimilarChunkResult from(SimilarChunk c) {
        return new SimilarChunkResult(c.id(), c.chunkIndex(), c.distance(), c.content());
    }
}
