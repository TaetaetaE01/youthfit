package com.youthfit.qna.domain.model;

public enum QnaFailedReason {
    NO_INDEXED_DOCUMENT,
    NO_RELEVANT_CHUNK,
    LLM_ERROR,
    COST_GUARD_BLOCKED,
    INTERNAL_ERROR
}
