package com.youthfit.admin.presentation.dto.response;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EnrichmentJob 의 평면 응답 DTO.
 */
public record EnrichmentJobResponse(
        Long id,
        Long policyId,
        EnrichmentJobStatus status,
        int attempt,
        String requestedBy,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<UrlResponse> requestedUrls
) {
    public static EnrichmentJobResponse of(EnrichmentJob job) {
        if (job == null) return null;
        List<UrlResponse> urls = job.getRequestedUrls() == null
                ? List.of()
                : job.getRequestedUrls().stream().map(UrlResponse::of).toList();
        return new EnrichmentJobResponse(
                job.getId(),
                job.getPolicyId(),
                job.getStatus(),
                job.getAttempt(),
                job.getRequestedBy(),
                job.getErrorMessage(),
                job.getRequestedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                urls
        );
    }
}
