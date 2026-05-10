package com.youthfit.qna.application.dto.result;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record CachedAnswer(
        String answer,
        List<QnaSourceResult> sources,
        List<String> followUpQuestions,
        Instant cachedAt
) {
    @JsonCreator
    public CachedAnswer(
            @JsonProperty("answer") String answer,
            @JsonProperty("sources") List<QnaSourceResult> sources,
            @JsonProperty("followUpQuestions") List<String> followUpQuestions,
            @JsonProperty("cachedAt") Instant cachedAt
    ) {
        this.answer = answer;
        this.sources = sources != null ? sources : List.of();
        this.followUpQuestions = followUpQuestions != null ? followUpQuestions : List.of();
        this.cachedAt = cachedAt;
    }
}
