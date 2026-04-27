package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public Map<Long, PolicySource> findFirstByPolicyIds(List<Long> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return Map.of();
        }
        List<PolicySource> all = jpaRepository.findAllByPolicyIdInOrderByIdAsc(policyIds);
        Map<Long, PolicySource> result = new HashMap<>();
        for (PolicySource source : all) {
            result.putIfAbsent(source.getPolicy().getId(), source);
        }
        return result;
    }

    @Override
    public PolicySource save(PolicySource policySource) {
        return jpaRepository.save(policySource);
    }
}
