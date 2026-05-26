package com.youthfit.ingestion.presentation.dto.request;

import com.youthfit.policy.domain.model.PolicyEnrichment;
import jakarta.validation.constraints.NotNull;

public record ApplyPolicyEnrichmentRequest(
        @NotNull Long policyId,
        @NotNull PolicyEnrichment enrichmentResult
) {
}
