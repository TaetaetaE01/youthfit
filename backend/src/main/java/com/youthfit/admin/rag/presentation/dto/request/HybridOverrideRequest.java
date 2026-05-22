package com.youthfit.admin.rag.presentation.dto.request;

import com.youthfit.admin.rag.application.dto.command.HybridOverrideCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record HybridOverrideRequest(
        Boolean hybridEnabled,
        @Min(1) @Max(100) Integer topNPerSearch,
        @Min(1) @Max(500) Integer rrfK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double trigramThreshold,
        Boolean keywordBoostEnabled,
        @Min(0) @Max(20) Integer maxKeywords
) {
    public HybridOverrideCommand toCommand() {
        return new HybridOverrideCommand(
                hybridEnabled, topNPerSearch, rrfK,
                trigramThreshold, keywordBoostEnabled, maxKeywords);
    }
}
