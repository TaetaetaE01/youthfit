package com.youthfit.admin.presentation.dto.response;

import com.youthfit.qna.domain.model.LookupResultType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QnaCacheLookupSummaryResponse(
        Long id,
        LocalDateTime lookedUpAt,
        LookupResultType result,
        Long policyId,
        String questionExcerpt,
        BigDecimal similarityScore,
        Long matchedCachedId
) {}
