package com.youthfit.user.application.dto.command;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.user.domain.model.RegionSidoCode;

import java.util.Set;

public record UpdateNotificationSettingCommand(
        boolean emailEnabled,
        int daysBeforeDeadline,
        boolean recommendationEnabled,
        Set<Category> interestCategories,
        Set<RegionSidoCode> interestRegions
) {
    public UpdateNotificationSettingCommand {
        interestCategories = interestCategories == null ? Set.of() : Set.copyOf(interestCategories);
        interestRegions = interestRegions == null ? Set.of() : Set.copyOf(interestRegions);
    }
}
