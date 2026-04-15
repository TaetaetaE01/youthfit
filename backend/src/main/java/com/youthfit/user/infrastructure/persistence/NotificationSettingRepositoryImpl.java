package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationSettingRepositoryImpl implements NotificationSettingRepository {

    private final NotificationSettingJpaRepository jpaRepository;

    @Override
    public Optional<NotificationSetting> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public List<NotificationSetting> findAllByEmailEnabled(boolean emailEnabled) {
        return jpaRepository.findAllByEmailEnabled(emailEnabled);
    }

    @Override
    public NotificationSetting save(NotificationSetting notificationSetting) {
        return jpaRepository.save(notificationSetting);
    }
}
