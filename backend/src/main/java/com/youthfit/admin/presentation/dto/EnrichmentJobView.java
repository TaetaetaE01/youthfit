package com.youthfit.admin.presentation.dto;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EnrichmentJob 의 평면 응답 뷰.
 */
public record EnrichmentJobView(
        Long id,
        Long policyId,
        EnrichmentJobStatus status,
        int attempt,
        String requestedBy,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<UrlView> requestedUrls
) {
    public static EnrichmentJobView of(EnrichmentJob job) {
        if (job == null) return null;
        List<UrlView> urls = job.getRequestedUrls() == null
                ? List.of()
                : job.getRequestedUrls().stream().map(UrlView::of).toList();
        return new EnrichmentJobView(
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
