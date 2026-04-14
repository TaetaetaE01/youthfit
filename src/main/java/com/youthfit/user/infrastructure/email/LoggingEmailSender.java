package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.port.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendDeadlineNotification(String recipientEmail, Policy policy) {
        log.info("[이메일 발송] 수신: {}, 정책명: {}, 마감일: {}, 정책 ID: {}",
                recipientEmail,
                policy.getTitle(),
                policy.getApplyEnd(),
                policy.getId());
    }
}
