// backend/src/main/java/com/youthfit/rag/application/dto/result/RagSearchTrace.java
package com.youthfit.rag.application.dto.result;

import com.youthfit.rag.domain.model.SimilarChunk;

import java.util.List;

public record RagSearchTrace(
        EffectiveConfig effective,
        List<SimilarChunk> vectorTopN,
        List<SimilarChunk> trigramTopN,
        List<MergedChunk> merged,
        List<String> usedKeywords,
        long tookMs
) {}
