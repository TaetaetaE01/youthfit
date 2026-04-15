package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.NotificationSetting;

import java.time.LocalDateTime;

public record NotificationSettingResult(
        boolean emailEnabled,
        int daysBeforeDeadline,
        LocalDateTime updatedAt
) {

    public static NotificationSettingResult from(NotificationSetting setting) {
        return new NotificationSettingResult(
                setting.isEmailEnabled(),
                setting.getDaysBeforeDeadline(),
                setting.getUpdatedAt()
        );
    }
}
