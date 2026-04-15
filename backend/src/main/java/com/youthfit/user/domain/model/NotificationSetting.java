package com.youthfit.user.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_setting")
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "days_before_deadline", nullable = false)
    private int daysBeforeDeadline;

    public NotificationSetting(Long userId) {
        this.userId = userId;
        this.emailEnabled = true;
        this.daysBeforeDeadline = 7;
    }

    public void updateSetting(boolean emailEnabled, int daysBeforeDeadline) {
        this.emailEnabled = emailEnabled;
        this.daysBeforeDeadline = daysBeforeDeadline;
    }
}
