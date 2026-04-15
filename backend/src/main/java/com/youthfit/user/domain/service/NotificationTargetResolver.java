package com.youthfit.user.domain.service;

import com.youthfit.policy.domain.model.Policy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class NotificationTargetResolver {

    private NotificationTargetResolver() {
    }

    public static boolean shouldNotify(Policy policy, int daysBeforeDeadline, LocalDate today) {
        if (!policy.isOpen()) {
            return false;
        }
        if (policy.getApplyEnd() == null) {
            return false;
        }
        long daysUntilDeadline = ChronoUnit.DAYS.between(today, policy.getApplyEnd());
        return daysUntilDeadline >= 0 && daysUntilDeadline <= daysBeforeDeadline;
    }
}
