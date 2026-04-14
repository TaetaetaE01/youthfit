package com.youthfit.user.application.dto.command;

public record UpdateNotificationSettingCommand(
        boolean emailEnabled,
        int daysBeforeDeadline
) {
}
