package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.EnrichmentJob;

import java.time.Instant;
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

    /**
     * {@code since} 시각 이후 종료된({@code finishedAt}) {@link EnrichmentJob} 중 상태가
     * {@code FAILED} 인 건수를 반환한다.
     *
     * <p>어드민 대시보드 신호 평가에 사용된다. 도메인 포트 경계에서는 {@link Instant} 를
     * 받지만, 구현체는 내부적으로 {@link LocalDateTime} 으로 변환하여 JPA 컬럼과 비교한다.</p>
     */
    long countFailedSince(Instant since);
}
