package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import com.youthfit.user.application.email.EmailSendResult;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import com.youthfit.user.domain.exception.EmailSendException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "youthfit.email.transport", havingValue = "ses")
public class SesEmailSender implements EmailSender {

    private static final String CHARSET = "UTF-8";

    private final SesV2Client sesClient;
    private final NotificationEmailRenderer renderer;
    private final String fromAddress;
    private final String fromName;

    public SesEmailSender(SesV2Client sesClient,
                          NotificationEmailRenderer renderer,
                          @Value("${youthfit.email.from.address}") String fromAddress,
                          @Value("${youthfit.email.from.name:YouthFit}") String fromName) {
        this.sesClient = sesClient;
        this.renderer = renderer;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public EmailSendResult sendDeadlineNotification(String recipientEmail, Policy policy) {
        EmailContent content = renderer.renderDeadline(policy);
        String messageId = sendInternal(recipientEmail, content, "DEADLINE");
        return new EmailSendResult(messageId, content.subject());
    }

    @Override
    public EmailSendResult sendRecommendationNotification(String recipientEmail, List<Policy> policies) {
        EmailContent content = renderer.renderRecommendation(policies);
        String messageId = sendInternal(recipientEmail, content, "RECOMMENDATION");
        return new EmailSendResult(messageId, content.subject());
    }

    private String sendInternal(String recipientEmail, EmailContent content, String type) {
        try {
            SendEmailResponse response = sesClient.sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(formatFrom(fromAddress, fromName))
                    .destination(Destination.builder().toAddresses(recipientEmail).build())
                    .content(software.amazon.awssdk.services.sesv2.model.EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(charsetContent(content.subject()))
                                    .body(Body.builder()
                                            .html(charsetContent(content.htmlBody()))
                                            .text(charsetContent(content.textBody()))
                                            .build())
                                    .build())
                            .build())
                    .build());
            log.info("SES 발송 성공 to={} type={} messageId={}", recipientEmail, type, response.messageId());
            return response.messageId();
        } catch (SdkException e) {
            log.error("SES 발송 실패 to={} type={}", recipientEmail, type, e);
            throw new EmailSendException("SES 발송 실패: " + recipientEmail, e);
        }
    }

    private static Content charsetContent(String data) {
        return Content.builder().data(data).charset(CHARSET).build();
    }

    private static String formatFrom(String address, String name) {
        if (name == null || name.isBlank()) {
            return address;
        }
        return name + " <" + address + ">";
    }
}
