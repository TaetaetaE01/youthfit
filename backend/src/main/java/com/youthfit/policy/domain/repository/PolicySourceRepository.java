package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PolicySourceRepository {

    Optional<PolicySource> findBySourceTypeAndExternalId(SourceType sourceType, String externalId);

    Optional<PolicySource> findFirstByPolicyId(Long policyId);

    Map<Long, PolicySource> findFirstByPolicyIds(List<Long> policyIds);

    PolicySource save(PolicySource policySource);
}
