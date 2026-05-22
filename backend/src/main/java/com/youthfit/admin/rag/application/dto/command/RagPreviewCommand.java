package com.youthfit.admin.rag.application.dto.command;

public record RagPreviewCommand(
        long userId,
        long policyId,
        String query,
        HybridOverrideCommand candidate
) {}
