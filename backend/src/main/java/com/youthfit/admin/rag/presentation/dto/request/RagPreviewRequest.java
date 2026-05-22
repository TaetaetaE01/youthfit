package com.youthfit.admin.rag.presentation.dto.request;

import com.youthfit.admin.rag.application.dto.command.RagPreviewCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RagPreviewRequest(
        @NotNull @Positive Long policyId,
        @NotBlank @Size(min = 1, max = 500) String query,
        @Valid HybridOverrideRequest candidate
) {
    public RagPreviewCommand toCommand(long userId) {
        return new RagPreviewCommand(
                userId, policyId, query,
                candidate == null ? null : candidate.toCommand());
    }
}
