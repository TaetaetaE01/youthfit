package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PolicySourceRepositoryImpl implements PolicySourceRepository {

    private final PolicySourceJpaRepository jpaRepository;

    public PolicySourceRepositoryImpl(PolicySourceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<PolicySource> findBySourceTypeAndExternalId(SourceType sourceType, String externalId) {
        return jpaRepository.findBySourceTypeAndExternalId(sourceType, externalId);
    }

    @Override
    public Optional<PolicySource> findFirstByPolicyId(Long policyId) {
        return jpaRepository.findFirstByPolicyIdOrderByIdAsc(policyId);
    }

    @Override
    public PolicySource save(PolicySource policySource) {
        return jpaRepository.save(policySource);
    }
}
