package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import com.youthfit.user.application.email.EmailSendResult;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "youthfit.email.transport", havingValue = "logging", matchIfMissing = true)
@RequiredArgsConstructor
public class LoggingEmailSender implements EmailSender {

    private static final int LOG_PREVIEW_LENGTH = 200;

    private final NotificationEmailRenderer renderer;

    @Override
    public EmailSendResult sendDeadlineNotification(String recipientEmail, Policy policy) {
        EmailContent content = renderer.renderDeadline(policy);
        String fakeId = "logging-" + UUID.randomUUID();
        log.info("[이메일 발송][마감][logging] to={} subject={} fakeId={} htmlPreview={}",
                recipientEmail,
                content.subject(),
                fakeId,
                preview(content.htmlBody()));
        return new EmailSendResult(fakeId, content.subject());
    }

    @Override
    public EmailSendResult sendRecommendationNotification(String recipientEmail, List<Policy> policies) {
        EmailContent content = renderer.renderRecommendation(policies);
        String fakeId = "logging-" + UUID.randomUUID();
        log.info("[이메일 발송][추천][logging] to={} subject={} fakeId={} count={} htmlPreview={}",
                recipientEmail,
                content.subject(),
                fakeId,
                policies.size(),
                preview(content.htmlBody()));
        return new EmailSendResult(fakeId, content.subject());
    }

    private String preview(String body) {
        if (body == null) return "";
        return body.length() <= LOG_PREVIEW_LENGTH
                ? body
                : body.substring(0, LOG_PREVIEW_LENGTH) + "...";
    }
}
