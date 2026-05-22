package com.youthfit.rag.application.dto.result;

import java.util.List;

public record RagSearchTrace(
        EffectiveConfig effective,
        List<SimilarChunkResult> vectorTopN,
        List<SimilarChunkResult> trigramTopN,
        List<MergedChunk> merged,
        List<String> usedKeywords,
        long tookMs
) {}
