package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyProcessingStepJpaRepository extends JpaRepository<PolicyProcessingStep, Long> {

    List<PolicyProcessingStep> findByPolicyIdOrderByStepAscAttemptAsc(Long policyId);

    Optional<PolicyProcessingStep> findTopByPolicyIdAndStepOrderByAttemptDesc(Long policyId, ProcessingStep step);

    int countByPolicyIdAndStep(Long policyId, ProcessingStep step);

    @Query("""
        select pps from PolicyProcessingStep pps
        where pps.policyId in :policyIds
          and pps.attempt = (
            select max(p2.attempt) from PolicyProcessingStep p2
            where p2.policyId = pps.policyId and p2.step = pps.step
          )
    """)
    List<PolicyProcessingStep> findLatestForPolicies(@Param("policyIds") List<Long> policyIds);

    @Query("""
        select pps from PolicyProcessingStep pps
        where pps.policyId = :policyId
          and pps.attempt = (
            select max(p2.attempt) from PolicyProcessingStep p2
            where p2.policyId = :policyId and p2.step = pps.step
          )
        order by pps.step
    """)
    List<PolicyProcessingStep> findLatestByPolicyIdAllSteps(@Param("policyId") Long policyId);
}
