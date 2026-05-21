package com.youthfit.ingestion.presentation.dto.request;

import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import jakarta.validation.constraints.NotNull;

public record EnrichmentCallbackRequest(
        @NotNull EnrichmentJobStatus status,
        String error
) { }
