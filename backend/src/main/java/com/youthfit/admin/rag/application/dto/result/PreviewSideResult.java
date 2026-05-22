package com.youthfit.admin.rag.application.dto.result;

import com.youthfit.rag.application.dto.result.RagSearchTrace;

/**
 * baseline / candidate 한 쪽의 trace 를 그대로 노출.
 */
public record PreviewSideResult(RagSearchTrace trace) {}
