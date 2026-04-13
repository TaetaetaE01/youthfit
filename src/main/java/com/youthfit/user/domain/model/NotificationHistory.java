package com.youthfit.user.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "notification_history", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_user_policy_type",
                columnNames = {"user_id", "policy_id", "notification_type"})
})
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "notification_type", nullable = false, length = 20)
    private String notificationType;

    @CreatedDate
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    public NotificationHistory(Long userId, Long policyId, String notificationType) {
        this.userId = userId;
        this.policyId = policyId;
        this.notificationType = notificationType;
    }
}
