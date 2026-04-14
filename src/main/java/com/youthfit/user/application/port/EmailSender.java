package com.youthfit.user.application.port;

import com.youthfit.policy.domain.model.Policy;

public interface EmailSender {

    void sendDeadlineNotification(String recipientEmail, Policy policy);
}
