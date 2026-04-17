package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.PolicyNotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyNotificationSubscriptionJpaRepository
        extends JpaRepository<PolicyNotificationSubscription, Long> {

    Optional<PolicyNotificationSubscription> findByUserIdAndPolicyId(Long userId, Long policyId);

    boolean existsByUserIdAndPolicyId(Long userId, Long policyId);

    List<PolicyNotificationSubscription> findAllByUserId(Long userId);
}
