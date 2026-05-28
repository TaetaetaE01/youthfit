package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyProcessingStepJpaRepository extends JpaRepository<PolicyProcessingStep, Long> {

    List<PolicyProcessingStep> findByPolicyIdOrderByStepAscAttemptAsc(Long policyId);

    Optional<PolicyProcessingStep> findTopByPolicyIdAndStepOrderByAttemptDesc(Long policyId, ProcessingStep step);

    int countByPolicyIdAndStep(Long policyId, ProcessingStep step);
}
