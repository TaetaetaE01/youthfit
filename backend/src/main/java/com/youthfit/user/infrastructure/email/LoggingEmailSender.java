package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.port.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendDeadlineNotification(String recipientEmail, Policy policy) {
        log.info("[이메일 발송][마감] 수신: {}, 정책명: {}, 마감일: {}, 정책 ID: {}",
                recipientEmail,
                policy.getTitle(),
                policy.getApplyEnd(),
                policy.getId());
    }

    @Override
    public void sendRecommendationNotification(String recipientEmail, List<Policy> policies) {
        String summary = policies.stream()
                .map(p -> "[" + p.getId() + "] " + p.getTitle() + " (마감 " + p.getApplyEnd() + ")")
                .collect(Collectors.joining(", "));
        log.info("[이메일 발송][추천] 수신: {}, 추천 {}건: {}",
                recipientEmail, policies.size(), summary);
    }
}
