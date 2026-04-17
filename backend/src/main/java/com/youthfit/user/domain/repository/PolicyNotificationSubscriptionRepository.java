package com.youthfit.user.domain.repository;

import com.youthfit.user.domain.model.PolicyNotificationSubscription;

import java.util.List;
import java.util.Optional;

public interface PolicyNotificationSubscriptionRepository {

    PolicyNotificationSubscription save(PolicyNotificationSubscription subscription);

    Optional<PolicyNotificationSubscription> findByUserIdAndPolicyId(Long userId, Long policyId);

    boolean existsByUserIdAndPolicyId(Long userId, Long policyId);

    List<PolicyNotificationSubscription> findAllByUserId(Long userId);

    void delete(PolicyNotificationSubscription subscription);
}
