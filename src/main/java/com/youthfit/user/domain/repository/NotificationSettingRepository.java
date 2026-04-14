package com.youthfit.user.domain.repository;

import com.youthfit.user.domain.model.NotificationSetting;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository {

    Optional<NotificationSetting> findByUserId(Long userId);

    List<NotificationSetting> findAllByEmailEnabled(boolean emailEnabled);

    NotificationSetting save(NotificationSetting notificationSetting);
}
