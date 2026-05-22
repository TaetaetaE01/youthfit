// backend/src/main/java/com/youthfit/rag/application/dto/result/MergedChunk.java
package com.youthfit.rag.application.dto.result;

/**
 * RRF 머지 후 한 청크의 표시 정보.
 * distance 는 ReciprocalRankFusion 이 유지한 SimilarChunk.distance 를 그대로 보존 (vector 우선).
 */
public record MergedChunk(
        long chunkId,
        int chunkIndex,
        double distance,
        double rrfScore,
        int rank,
        String preview
) {}
