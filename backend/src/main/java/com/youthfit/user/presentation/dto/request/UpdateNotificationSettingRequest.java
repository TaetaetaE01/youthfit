package com.youthfit.user.presentation.dto.request;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.user.application.dto.command.UpdateNotificationSettingCommand;
import com.youthfit.user.domain.model.RegionSidoCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateNotificationSettingRequest(
        @NotNull(message = "이메일 알림 수신 여부는 필수입니다")
        Boolean emailEnabled,

        @NotNull(message = "알림 시점(daysBeforeDeadline)은 필수입니다")
        Integer daysBeforeDeadline,

        @NotNull(message = "추천 알림 수신 여부는 필수입니다")
        Boolean recommendationEnabled,

        Set<Category> interestCategories,
        Set<RegionSidoCode> interestRegions
) {

    @AssertTrue(message = "알림 시점은 3, 7, 14 중 하나여야 합니다")
    public boolean isDaysBeforeDeadlineValid() {
        if (daysBeforeDeadline == null) return true;
        return daysBeforeDeadline == 3 || daysBeforeDeadline == 7 || daysBeforeDeadline == 14;
    }

    @AssertTrue(message = "추천 알림 활성화 시 카테고리 또는 지역을 1개 이상 선택해야 합니다")
    public boolean isInterestSelectionValid() {
        if (recommendationEnabled == null || !recommendationEnabled) return true;
        int totalSize = (interestCategories == null ? 0 : interestCategories.size())
                + (interestRegions == null ? 0 : interestRegions.size());
        return totalSize > 0;
    }

    public UpdateNotificationSettingCommand toCommand() {
        return new UpdateNotificationSettingCommand(
                emailEnabled,
                daysBeforeDeadline,
                recommendationEnabled,
                interestCategories,
                interestRegions
        );
    }
}
