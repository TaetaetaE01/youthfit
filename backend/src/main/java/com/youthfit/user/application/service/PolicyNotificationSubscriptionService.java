package com.youthfit.user.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.dto.result.PolicyNotificationSubscriptionResult;
import com.youthfit.user.domain.model.PolicyNotificationSubscription;
import com.youthfit.user.domain.repository.PolicyNotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyNotificationSubscriptionService {

    private final PolicyNotificationSubscriptionRepository subscriptionRepository;
    private final PolicyRepository policyRepository;

    @Transactional
    public PolicyNotificationSubscriptionResult subscribe(Long userId, Long policyId) {
        policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다"));

        PolicyNotificationSubscription subscription = subscriptionRepository
                .findByUserIdAndPolicyId(userId, policyId)
                .orElseGet(() -> subscriptionRepository.save(
                        new PolicyNotificationSubscription(userId, policyId)
                ));

        return PolicyNotificationSubscriptionResult.subscribed(subscription);
    }

    @Transactional
    public void unsubscribe(Long userId, Long policyId) {
        subscriptionRepository.findByUserIdAndPolicyId(userId, policyId)
                .ifPresent(subscriptionRepository::delete);
    }

    @Transactional(readOnly = true)
    public PolicyNotificationSubscriptionResult findSubscription(Long userId, Long policyId) {
        return subscriptionRepository.findByUserIdAndPolicyId(userId, policyId)
                .map(PolicyNotificationSubscriptionResult::subscribed)
                .orElseGet(() -> PolicyNotificationSubscriptionResult.unsubscribed(policyId));
    }
}
