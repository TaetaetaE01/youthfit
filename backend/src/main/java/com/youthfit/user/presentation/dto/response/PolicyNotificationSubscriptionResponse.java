package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.PolicyNotificationSubscriptionResult;

import java.time.LocalDateTime;

public record PolicyNotificationSubscriptionResponse(
        Long subscriptionId,
        Long policyId,
        boolean subscribed,
        LocalDateTime subscribedAt
) {

    public static PolicyNotificationSubscriptionResponse from(PolicyNotificationSubscriptionResult result) {
        return new PolicyNotificationSubscriptionResponse(
                result.subscriptionId(),
                result.policyId(),
                result.subscribed(),
                result.subscribedAt()
        );
    }
}
