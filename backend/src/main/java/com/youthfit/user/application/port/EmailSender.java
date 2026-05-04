package com.youthfit.user.application.port;

import com.youthfit.policy.domain.model.Policy;

import java.util.List;

public interface EmailSender {

    void sendDeadlineNotification(String recipientEmail, Policy policy);

    void sendRecommendationNotification(String recipientEmail, List<Policy> policies);
}
