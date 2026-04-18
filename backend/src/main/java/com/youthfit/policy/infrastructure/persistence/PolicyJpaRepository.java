package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PolicyJpaRepository extends JpaRepository<Policy, Long>,
        JpaSpecificationExecutor<Policy> {
}
