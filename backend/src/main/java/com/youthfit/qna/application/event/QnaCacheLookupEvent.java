package com.youthfit.qna.application.event;

import com.youthfit.qna.domain.model.LookupResultType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QnaCacheLookupEvent(
        Long policyId,
        String questionText,
        String normalizedText,
        LookupResultType result,
        Long matchedCachedId,
        BigDecimal similarityScore,
        BigDecimal thresholdApplied,
        boolean llmCallMade,
        LocalDateTime lookedUpAt
) {}
