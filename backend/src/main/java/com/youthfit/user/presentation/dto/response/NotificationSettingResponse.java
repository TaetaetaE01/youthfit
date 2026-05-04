package com.youthfit.user.presentation.dto.response;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.user.application.dto.result.NotificationSettingResult;
import com.youthfit.user.domain.model.RegionSidoCode;

import java.time.LocalDateTime;
import java.util.Set;

public record NotificationSettingResponse(
        boolean emailEnabled,
        int daysBeforeDeadline,
        boolean recommendationEnabled,
        Set<Category> interestCategories,
        Set<RegionSidoCode> interestRegions,
        LocalDateTime updatedAt
) {

    public static NotificationSettingResponse from(NotificationSettingResult result) {
        return new NotificationSettingResponse(
                result.emailEnabled(),
                result.daysBeforeDeadline(),
                result.recommendationEnabled(),
                result.interestCategories(),
                result.interestRegions(),
                result.updatedAt()
        );
    }
}
