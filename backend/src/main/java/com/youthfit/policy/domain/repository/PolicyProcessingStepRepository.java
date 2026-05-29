package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PolicyProcessingStepRepository {

    PolicyProcessingStep save(PolicyProcessingStep step);

    Optional<PolicyProcessingStep> findById(Long id);

    List<PolicyProcessingStep> findByPolicyIdOrderByStep(Long policyId);

    /** 동일 (policy_id, step) 의 가장 큰 attempt 행 1건. */
    Optional<PolicyProcessingStep> findLatestByPolicyIdAndStep(Long policyId, ProcessingStep step);

    int countByPolicyIdAndStep(Long policyId, ProcessingStep step);

    /**
     * 정책 다건의 각 step 별 최신 attempt status 일괄 조회.
     *
     * @return policyId -> (step -> latestStatus). 정책에 step 행이 없으면 그 정책 키 자체가 없음.
     */
    Map<Long, Map<ProcessingStep, ProcessingStatus>> findLatestStatusMapByPolicyIds(List<Long> policyIds);

    /** 정책의 5단계 각 step 의 최신 attempt 행 전체 반환 (펼침 영역 표시용). */
    List<PolicyProcessingStep> findLatestRowsByPolicyId(Long policyId);
}
