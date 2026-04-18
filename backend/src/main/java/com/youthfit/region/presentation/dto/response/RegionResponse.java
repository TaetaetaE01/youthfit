package com.youthfit.region.presentation.dto.response;

import com.youthfit.region.application.dto.result.RegionResult;
import com.youthfit.region.domain.model.RegionLevel;

public record RegionResponse(
        String code,
        String name,
        RegionLevel level,
        String parentCode
) {
    public static RegionResponse from(RegionResult result) {
        return new RegionResponse(result.code(), result.name(), result.level(), result.parentCode());
    }
}
