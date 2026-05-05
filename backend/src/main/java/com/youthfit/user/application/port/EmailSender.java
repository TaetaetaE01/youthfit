package com.youthfit.user.application.port;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.email.EmailSendResult;

import java.util.List;

public interface EmailSender {

    EmailSendResult sendDeadlineNotification(String recipientEmail, Policy policy);

    EmailSendResult sendRecommendationNotification(String recipientEmail, List<Policy> policies);
}
