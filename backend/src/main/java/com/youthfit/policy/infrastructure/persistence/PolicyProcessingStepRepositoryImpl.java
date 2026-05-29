package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Override
    public Map<Long, Map<ProcessingStep, ProcessingStatus>> findLatestStatusMapByPolicyIds(List<Long> policyIds) {
        if (policyIds.isEmpty()) return Map.of();
        List<PolicyProcessingStep> rows = jpaRepository.findLatestForPolicies(policyIds);
        return rows.stream().collect(Collectors.groupingBy(
                PolicyProcessingStep::getPolicyId,
                Collectors.toMap(
                        PolicyProcessingStep::getStep,
                        PolicyProcessingStep::getStatus,
                        (a, b) -> b
                )
        ));
    }

    @Override
    public List<PolicyProcessingStep> findLatestRowsByPolicyId(Long policyId) {
        return jpaRepository.findLatestByPolicyIdAllSteps(policyId);
    }

    @Override
    public List<PolicyProcessingStep> findActiveStaleBefore(Instant threshold) {
        return jpaRepository.findByStatusAndStartedAtBefore(ProcessingStatus.IN_PROGRESS, threshold);
    }
}
