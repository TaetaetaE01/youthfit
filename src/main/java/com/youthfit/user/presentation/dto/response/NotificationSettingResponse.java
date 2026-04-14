package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.NotificationSettingResult;

import java.time.LocalDateTime;

public record NotificationSettingResponse(
        boolean emailEnabled,
        int daysBeforeDeadline,
        LocalDateTime updatedAt
) {

    public static NotificationSettingResponse from(NotificationSettingResult result) {
        return new NotificationSettingResponse(
                result.emailEnabled(),
                result.daysBeforeDeadline(),
                result.updatedAt()
        );
    }
}
