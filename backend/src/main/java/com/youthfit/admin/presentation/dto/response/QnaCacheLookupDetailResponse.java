package com.youthfit.admin.presentation.dto.response;

import com.youthfit.qna.domain.model.LookupResultType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QnaCacheLookupDetailResponse(
        Long id,
        LocalDateTime lookedUpAt,
        LookupResultType result,
        Long policyId,
        String questionText,
        String normalizedText,
        Long matchedCachedId,
        String matchedCachedQuestion,
        String matchedCachedAnswerExcerpt,
        BigDecimal similarityScore,
        BigDecimal thresholdApplied,
        boolean llmCallMade
) {}
