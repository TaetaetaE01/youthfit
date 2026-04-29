package com.youthfit.qna.application.dto.result;

import java.time.Instant;
import java.util.List;

public record CachedAnswer(
        String answer,
        List<QnaSourceResult> sources,
        Instant cachedAt
) {
}
