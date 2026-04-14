package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationHistoryJpaRepository extends JpaRepository<NotificationHistory, Long> {

    boolean existsByUserIdAndPolicyIdAndNotificationType(Long userId, Long policyId, String notificationType);
}
