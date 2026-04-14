package com.youthfit.eligibility.infrastructure.persistence;

import com.youthfit.eligibility.domain.model.EligibilityRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EligibilityRuleJpaRepository extends JpaRepository<EligibilityRule, Long> {

    List<EligibilityRule> findAllByPolicyId(Long policyId);
}
