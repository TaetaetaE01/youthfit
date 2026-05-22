package com.youthfit.admin.rag.application.dto.command;

public record HybridOverrideCommand(
        Boolean hybridEnabled,
        Integer topNPerSearch,
        Integer rrfK,
        Double trigramThreshold,
        Boolean keywordBoostEnabled,
        Integer maxKeywords
) {}
