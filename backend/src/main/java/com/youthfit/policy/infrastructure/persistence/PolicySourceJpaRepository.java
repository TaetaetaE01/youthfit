package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicySourceJpaRepository extends JpaRepository<PolicySource, Long> {

    Optional<PolicySource> findBySourceTypeAndExternalId(SourceType sourceType, String externalId);

    Optional<PolicySource> findFirstByPolicyIdOrderByIdAsc(Long policyId);

    List<PolicySource> findAllByPolicyIdInOrderByIdAsc(List<Long> policyIds);
}
