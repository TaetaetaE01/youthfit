package com.youthfit.rag.application.dto.result;

public record IndexingResult(Long policyId, int chunkCount, boolean updated) {
}
