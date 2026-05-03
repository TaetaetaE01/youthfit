package com.youthfit.eligibility.infrastructure.persistence;

import com.youthfit.eligibility.domain.model.EligibilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EligibilityRuleJpaRepository extends JpaRepository<EligibilityRule, Long> {

    List<EligibilityRule> findAllByPolicyId(Long policyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EligibilityRule r where r.policyId = :policyId")
    int deleteAllByPolicyId(@Param("policyId") Long policyId);
}
