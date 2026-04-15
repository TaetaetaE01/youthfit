package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationHistoryRepositoryImpl implements NotificationHistoryRepository {

    private final NotificationHistoryJpaRepository jpaRepository;

    @Override
    public NotificationHistory save(NotificationHistory notificationHistory) {
        return jpaRepository.save(notificationHistory);
    }

    @Override
    public boolean existsByUserIdAndPolicyIdAndNotificationType(Long userId, Long policyId, String notificationType) {
        return jpaRepository.existsByUserIdAndPolicyIdAndNotificationType(userId, policyId, notificationType);
    }
}
