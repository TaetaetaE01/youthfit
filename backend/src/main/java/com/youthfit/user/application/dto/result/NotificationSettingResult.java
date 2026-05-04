package com.youthfit.user.application.dto.result;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.RegionSidoCode;

import java.time.LocalDateTime;
import java.util.Set;

public record NotificationSettingResult(
        boolean emailEnabled,
        int daysBeforeDeadline,
        boolean recommendationEnabled,
        Set<Category> interestCategories,
        Set<RegionSidoCode> interestRegions,
        LocalDateTime updatedAt
) {

    public static NotificationSettingResult from(NotificationSetting setting) {
        return new NotificationSettingResult(
                setting.isEmailEnabled(),
                setting.getDaysBeforeDeadline(),
                setting.isRecommendationEnabled(),
                Set.copyOf(setting.getInterestCategories()),
                Set.copyOf(setting.getInterestRegions()),
                setting.getUpdatedAt()
        );
    }
}
