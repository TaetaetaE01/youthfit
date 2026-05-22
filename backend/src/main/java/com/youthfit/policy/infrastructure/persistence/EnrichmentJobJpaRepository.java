package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.EnrichmentJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EnrichmentJobJpaRepository extends JpaRepository<EnrichmentJob, Long> {

    @Query("""
            SELECT j FROM EnrichmentJob j
             WHERE j.policyId = :policyId
               AND j.status IN (com.youthfit.policy.domain.model.EnrichmentJobStatus.PENDING,
                                com.youthfit.policy.domain.model.EnrichmentJobStatus.RUNNING)
            """)
    Optional<EnrichmentJob> findActiveByPolicyId(@Param("policyId") Long policyId);

    @Query("""
            SELECT COALESCE(MAX(j.attempt), 0)
              FROM EnrichmentJob j
             WHERE j.policyId = :policyId
            """)
    int maxAttemptByPolicyId(@Param("policyId") Long policyId);

    List<EnrichmentJob> findByPolicyIdOrderByRequestedAtDesc(Long policyId, Pageable pageable);

    @Query("""
            SELECT j FROM EnrichmentJob j
             WHERE j.status IN (com.youthfit.policy.domain.model.EnrichmentJobStatus.PENDING,
                                com.youthfit.policy.domain.model.EnrichmentJobStatus.RUNNING)
               AND j.requestedAt < :threshold
            """)
    List<EnrichmentJob> findActiveStaleBefore(@Param("threshold") LocalDateTime threshold);

    long countByPolicyIdAndRequestedAtAfter(Long policyId, LocalDateTime since);

    @Query("""
            SELECT COUNT(j) FROM EnrichmentJob j
             WHERE j.status = com.youthfit.policy.domain.model.EnrichmentJobStatus.FAILED
               AND j.finishedAt >= :since
            """)
    long countFailedSince(@Param("since") LocalDateTime since);
}
