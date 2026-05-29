package com.youthfit.admin.application.dto;

public enum PolicyProcessingFilter {
    ALL,
    INCOMPLETE,
    PARTIAL,
    RAG_FAILED,
    ATTACHMENT_EMBEDDING_MISSING,
    REFERENCE_FETCH_FAILED,
    GUIDE_RULE_FAILED,
    RECENT_24H
}
