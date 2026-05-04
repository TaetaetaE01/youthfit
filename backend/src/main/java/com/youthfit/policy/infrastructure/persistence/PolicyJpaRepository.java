package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PolicyJpaRepository extends JpaRepository<Policy, Long>,
        JpaSpecificationExecutor<Policy> {

    List<Policy> findAllByStatus(PolicyStatus status);
}
