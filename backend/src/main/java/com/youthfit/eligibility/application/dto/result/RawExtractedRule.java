package com.youthfit.eligibility.application.dto.result;

public record RawExtractedRule(
        String field,
        String operator,
        String value,
        String label,
        String sourceReference,
        String confidence
) {
}
