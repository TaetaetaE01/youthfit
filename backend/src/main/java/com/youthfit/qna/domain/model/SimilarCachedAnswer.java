package com.youthfit.qna.domain.model;

public record SimilarCachedAnswer(
        Long id,
        String answer,
        String sourcesJson,
        double distance
) {
}
