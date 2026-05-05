package com.youthfit.user.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_send_attempt", indexes = {
    @Index(name = "idx_email_attempt_message_id", columnList = "ses_message_id"),
    @Index(name = "idx_email_attempt_history_id", columnList = "notification_history_id"),
    @Index(name = "idx_email_attempt_sent_at", columnList = "sent_at"),
    @Index(name = "idx_email_attempt_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailSendAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_history_id")
    private Long notificationHistoryId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 30)
    private NotificationType emailType;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", nullable = false, columnDefinition = "jsonb")
    private String inputPayloadJson;

    @Column(name = "ses_message_id", length = 255)
    private String sesMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailSendStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "bounce_type", length = 50)
    private String bounceType;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private EmailSendAttempt(Long historyId, Long userId, String email, NotificationType type,
                             String subject, String inputPayload, String sesMessageId,
                             EmailSendStatus status, String errorCode, String errorMessage,
                             LocalDateTime now) {
        this.notificationHistoryId = historyId;
        this.recipientUserId = userId;
        this.recipientEmail = email;
        this.emailType = type;
        this.subject = subject;
        this.inputPayloadJson = inputPayload;
        this.sesMessageId = sesMessageId;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.sentAt = now;
        this.updatedAt = now;
    }

    public static EmailSendAttempt success(Long historyId, Long userId, String email,
                                           NotificationType type, String sesMessageId,
                                           String subject, String inputPayload,
                                           LocalDateTime now) {
        return new EmailSendAttempt(historyId, userId, email, type, subject, inputPayload,
            sesMessageId, EmailSendStatus.SENT, null, null, now);
    }

    public static EmailSendAttempt failure(Long historyId, Long userId, String email,
                                           NotificationType type, String subject,
                                           String inputPayload, String errorCode,
                                           String errorMessage, LocalDateTime now) {
        return new EmailSendAttempt(historyId, userId, email, type, subject, inputPayload,
            null, EmailSendStatus.FAILED, errorCode, errorMessage, now);
    }

    public void markDelivered(LocalDateTime now) {
        if (status != EmailSendStatus.SENT) {
            throw new IllegalStateException("DELIVERED 전이는 SENT 에서만 허용: " + status);
        }
        this.status = EmailSendStatus.DELIVERED;
        this.updatedAt = now;
    }

    public void markBounced(LocalDateTime now, String bounceType, String reason) {
        if (status != EmailSendStatus.SENT) {
            throw new IllegalStateException("BOUNCED 전이는 SENT 에서만 허용: " + status);
        }
        this.status = EmailSendStatus.BOUNCED;
        this.bounceType = bounceType;
        this.errorMessage = reason;
        this.updatedAt = now;
    }

    public void markComplained(LocalDateTime now) {
        if (status != EmailSendStatus.SENT && status != EmailSendStatus.DELIVERED) {
            throw new IllegalStateException("COMPLAINED 전이는 SENT/DELIVERED 에서만 허용: " + status);
        }
        this.status = EmailSendStatus.COMPLAINED;
        this.updatedAt = now;
    }
}
