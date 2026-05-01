package com.youthfit.qna.domain.model;

public record SimilarCachedAnswer(
        Long id,
        String questionText,
        String sourceHash,
        String answer,
        String sourcesJson,
        double distance
) {
}
