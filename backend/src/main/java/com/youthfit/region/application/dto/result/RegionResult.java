package com.youthfit.region.application.dto.result;

import com.youthfit.region.domain.model.LegalDong;
import com.youthfit.region.domain.model.RegionLevel;

public record RegionResult(
        String code,
        String name,
        RegionLevel level,
        String parentCode
) {
    public static RegionResult from(LegalDong legalDong) {
        return new RegionResult(
                legalDong.getCode(),
                legalDong.displayName(),
                legalDong.getLevel(),
                legalDong.getParentCode()
        );
    }
}
