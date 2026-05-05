package com.youthfit.qna.application.port.dto;

import java.math.BigDecimal;

public record SemanticLookupMatch(
        Long cachedId,
        BigDecimal similarity,
        BigDecimal distance
) {}
