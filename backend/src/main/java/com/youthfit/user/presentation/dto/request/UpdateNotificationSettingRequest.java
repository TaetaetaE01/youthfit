package com.youthfit.user.presentation.dto.request;

import com.youthfit.user.application.dto.command.UpdateNotificationSettingCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingRequest(
        @NotNull(message = "이메일 알림 수신 여부는 필수입니다")
        Boolean emailEnabled,

        @NotNull(message = "알림 시점(daysBeforeDeadline)은 필수입니다")
        Integer daysBeforeDeadline
) {

    @AssertTrue(message = "알림 시점은 3, 7, 14 중 하나여야 합니다")
    public boolean isDaysBeforeDeadlineValid() {
        if (daysBeforeDeadline == null) {
            return true;
        }
        return daysBeforeDeadline == 3 || daysBeforeDeadline == 7 || daysBeforeDeadline == 14;
    }

    public UpdateNotificationSettingCommand toCommand() {
        return new UpdateNotificationSettingCommand(emailEnabled, daysBeforeDeadline);
    }
}
