package com.youthfit.user.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_history", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_user_policy_type",
                columnNames = {"user_id", "policy_id", "notification_type"})
})
public class NotificationHistory {

    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 20)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    private NotificationHistory(Long userId, Long policyId, NotificationType notificationType,
                                NotificationStatus status, LocalDateTime createdAt) {
        this.userId = userId;
        this.policyId = policyId;
        this.notificationType = notificationType;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static NotificationHistory pending(Long userId, Long policyId, NotificationType notificationType) {
        return new NotificationHistory(userId, policyId, notificationType,
                NotificationStatus.PENDING, LocalDateTime.now());
    }

    public void markSent(LocalDateTime now) {
        if (this.status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태에서만 SENT 로 전이할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
    }

    public void markFailed(LocalDateTime now, String reason) {
        if (this.status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태에서만 FAILED 로 전이할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = NotificationStatus.FAILED;
        this.failedAt = now;
        this.failureReason = truncate(reason);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= FAILURE_REASON_MAX_LENGTH
                ? s
                : s.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
