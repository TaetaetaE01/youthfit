package com.youthfit.user.domain.repository;

import com.youthfit.user.domain.model.NotificationHistory;

public interface NotificationHistoryRepository {

    NotificationHistory save(NotificationHistory notificationHistory);

    boolean existsByUserIdAndPolicyIdAndNotificationType(Long userId, Long policyId, String notificationType);
}
