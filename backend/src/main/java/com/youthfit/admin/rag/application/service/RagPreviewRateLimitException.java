package com.youthfit.admin.rag.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;

public class RagPreviewRateLimitException extends YouthFitException {
    public RagPreviewRateLimitException() {
        super(ErrorCode.RAG_PREVIEW_RATE_LIMITED);
    }
}
