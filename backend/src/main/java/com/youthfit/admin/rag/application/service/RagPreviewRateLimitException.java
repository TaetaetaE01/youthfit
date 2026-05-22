package com.youthfit.admin.rag.application.service;

public class RagPreviewRateLimitException extends RuntimeException {
    public RagPreviewRateLimitException() {
        super("RAG preview rate limit exceeded (30/min)");
    }
}
