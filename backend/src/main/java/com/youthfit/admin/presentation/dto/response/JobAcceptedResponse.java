package com.youthfit.admin.presentation.dto.response;

import com.youthfit.policy.domain.model.EnrichmentJobStatus;

/**
 * EnrichmentJob 생성 응답 (202 Accepted) DTO.
 */
public record JobAcceptedResponse(
        Long jobId,
        EnrichmentJobStatus status
) { }
