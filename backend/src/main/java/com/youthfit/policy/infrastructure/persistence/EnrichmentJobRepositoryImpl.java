package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
public class EnrichmentJobRepositoryImpl implements EnrichmentJobRepository {

    private final EnrichmentJobJpaRepository jpa;

    public EnrichmentJobRepositoryImpl(EnrichmentJobJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override public EnrichmentJob save(EnrichmentJob job) { return jpa.save(job); }
    @Override public Optional<EnrichmentJob> findById(Long id) { return jpa.findById(id); }
    @Override public Optional<EnrichmentJob> findActiveByPolicyId(Long policyId) { return jpa.findActiveByPolicyId(policyId); }
    @Override public int maxAttemptByPolicyId(Long policyId) { return jpa.maxAttemptByPolicyId(policyId); }

    @Override
    public List<EnrichmentJob> findRecentByPolicyId(Long policyId, int limit) {
        return jpa.findByPolicyIdOrderByRequestedAtDesc(policyId, PageRequest.of(0, limit));
    }

    @Override
    public List<EnrichmentJob> findActiveStaleBefore(LocalDateTime threshold) {
        return jpa.findActiveStaleBefore(threshold);
    }

    @Override
    public long countRecentByPolicyId(Long policyId, LocalDateTime since) {
        return jpa.countByPolicyIdAndRequestedAtAfter(policyId, since);
    }

    @Override
    public long countFailedSince(Instant since) {
        LocalDateTime sinceLdt = LocalDateTime.ofInstant(since, ZoneId.systemDefault());
        return jpa.countFailedSince(sinceLdt);
    }
}
