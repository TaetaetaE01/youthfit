package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EnrichmentJobRepository {

    EnrichmentJob save(EnrichmentJob job);

    Optional<EnrichmentJob> findById(Long id);

    Optional<EnrichmentJob> findActiveByPolicyId(Long policyId);

    int maxAttemptByPolicyId(Long policyId);

    List<EnrichmentJob> findRecentByPolicyId(Long policyId, int limit);

    List<EnrichmentJob> findActiveStaleBefore(LocalDateTime threshold);

    long countRecentByPolicyId(Long policyId, LocalDateTime since);
}
