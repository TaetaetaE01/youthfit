package com.youthfit.qna.application.port.dto;

import com.youthfit.qna.application.dto.result.CachedAnswer;

import java.util.Optional;

public record SemanticLookupResult(
        Optional<SemanticLookupMatch> closest,
        Optional<CachedAnswer> cachedAnswer
) {
    public static SemanticLookupResult miss() {
        return new SemanticLookupResult(Optional.empty(), Optional.empty());
    }
    public static SemanticLookupResult belowThreshold(SemanticLookupMatch closest) {
        return new SemanticLookupResult(Optional.of(closest), Optional.empty());
    }
    public static SemanticLookupResult hit(SemanticLookupMatch closest, CachedAnswer answer) {
        return new SemanticLookupResult(Optional.of(closest), Optional.of(answer));
    }
}
