package com.youthfit.user.application.service;

import com.youthfit.user.application.dto.command.UpdateNotificationSettingCommand;
import com.youthfit.user.application.dto.result.NotificationSettingResult;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingRepository notificationSettingRepository;

    @Transactional
    public NotificationSettingResult findNotificationSetting(Long userId) {
        NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> notificationSettingRepository.save(new NotificationSetting(userId)));
        return NotificationSettingResult.from(setting);
    }

    @Transactional
    public NotificationSettingResult updateNotificationSetting(Long userId, UpdateNotificationSettingCommand command) {
        NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> notificationSettingRepository.save(new NotificationSetting(userId)));
        setting.updateSetting(command.emailEnabled(), command.daysBeforeDeadline());
        return NotificationSettingResult.from(setting);
    }
}
