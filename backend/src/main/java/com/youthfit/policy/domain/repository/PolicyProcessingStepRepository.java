package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStep;

import java.util.List;
import java.util.Optional;

public interface PolicyProcessingStepRepository {

    PolicyProcessingStep save(PolicyProcessingStep step);

    Optional<PolicyProcessingStep> findById(Long id);

    List<PolicyProcessingStep> findByPolicyIdOrderByStep(Long policyId);

    /** 동일 (policy_id, step) 의 가장 큰 attempt 행 1건. */
    Optional<PolicyProcessingStep> findLatestByPolicyIdAndStep(Long policyId, ProcessingStep step);

    int countByPolicyIdAndStep(Long policyId, ProcessingStep step);
}
