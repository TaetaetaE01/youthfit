package com.youthfit.eligibility.domain.repository;

import com.youthfit.eligibility.domain.model.EligibilityRule;

import java.util.List;

public interface EligibilityRuleRepository {

    List<EligibilityRule> findAllByPolicyId(Long policyId);
}
