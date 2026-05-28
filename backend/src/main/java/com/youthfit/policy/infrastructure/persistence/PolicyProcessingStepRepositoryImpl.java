package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PolicyProcessingStepRepositoryImpl implements PolicyProcessingStepRepository {

    private final PolicyProcessingStepJpaRepository jpaRepository;

    @Override
    public PolicyProcessingStep save(PolicyProcessingStep step) {
        return jpaRepository.save(step);
    }

    @Override
    public Optional<PolicyProcessingStep> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<PolicyProcessingStep> findByPolicyIdOrderByStep(Long policyId) {
        return jpaRepository.findByPolicyIdOrderByStepAscAttemptAsc(policyId);
    }

    @Override
    public Optional<PolicyProcessingStep> findLatestByPolicyIdAndStep(Long policyId, ProcessingStep step) {
        return jpaRepository.findTopByPolicyIdAndStepOrderByAttemptDesc(policyId, step);
    }

    @Override
    public int countByPolicyIdAndStep(Long policyId, ProcessingStep step) {
        return jpaRepository.countByPolicyIdAndStep(policyId, step);
    }
}
