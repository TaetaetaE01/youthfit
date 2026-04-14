package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingJpaRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByUserId(Long userId);

    List<NotificationSetting> findAllByEmailEnabled(boolean emailEnabled);
}
