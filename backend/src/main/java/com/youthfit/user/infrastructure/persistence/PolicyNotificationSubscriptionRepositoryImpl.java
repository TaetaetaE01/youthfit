package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.PolicyNotificationSubscription;
import com.youthfit.user.domain.repository.PolicyNotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PolicyNotificationSubscriptionRepositoryImpl
        implements PolicyNotificationSubscriptionRepository {

    private final PolicyNotificationSubscriptionJpaRepository jpaRepository;

    @Override
    public PolicyNotificationSubscription save(PolicyNotificationSubscription subscription) {
        return jpaRepository.save(subscription);
    }

    @Override
    public Optional<PolicyNotificationSubscription> findByUserIdAndPolicyId(Long userId, Long policyId) {
        return jpaRepository.findByUserIdAndPolicyId(userId, policyId);
    }

    @Override
    public boolean existsByUserIdAndPolicyId(Long userId, Long policyId) {
        return jpaRepository.existsByUserIdAndPolicyId(userId, policyId);
    }

    @Override
    public List<PolicyNotificationSubscription> findAllByUserId(Long userId) {
        return jpaRepository.findAllByUserId(userId);
    }

    @Override
    public void delete(PolicyNotificationSubscription subscription) {
        jpaRepository.delete(subscription);
    }
}
