package com.youthfit.eligibility.infrastructure.persistence;

import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class EligibilityRuleRepositoryImpl implements EligibilityRuleRepository {

    private final EligibilityRuleJpaRepository jpaRepository;

    @Override
    public List<EligibilityRule> findAllByPolicyId(Long policyId) {
        return jpaRepository.findAllByPolicyId(policyId);
    }
}
