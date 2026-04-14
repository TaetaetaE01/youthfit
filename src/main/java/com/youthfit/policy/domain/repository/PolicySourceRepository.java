package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;

import java.util.Optional;

public interface PolicySourceRepository {

    Optional<PolicySource> findBySourceTypeAndExternalId(SourceType sourceType, String externalId);

    PolicySource save(PolicySource policySource);
}
