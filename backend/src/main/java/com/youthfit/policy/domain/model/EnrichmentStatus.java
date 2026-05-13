package com.youthfit.policy.domain.model;

public enum EnrichmentStatus {
    OK,
    NO_LINK,
    FETCH_FAILED,
    TOO_SHORT,
    LLM_FAILED,
    PARSE_FAILED,
    LOW_CONFIDENCE
}
