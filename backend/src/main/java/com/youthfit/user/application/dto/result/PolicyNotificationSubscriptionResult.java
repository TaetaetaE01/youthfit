package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.PolicyNotificationSubscription;

import java.time.LocalDateTime;

public record PolicyNotificationSubscriptionResult(
        Long subscriptionId,
        Long policyId,
        boolean subscribed,
        LocalDateTime subscribedAt
) {

    public static PolicyNotificationSubscriptionResult subscribed(PolicyNotificationSubscription subscription) {
        return new PolicyNotificationSubscriptionResult(
                subscription.getId(),
                subscription.getPolicyId(),
                true,
                subscription.getCreatedAt()
        );
    }

    public static PolicyNotificationSubscriptionResult unsubscribed(Long policyId) {
        return new PolicyNotificationSubscriptionResult(null, policyId, false, null);
    }
}
