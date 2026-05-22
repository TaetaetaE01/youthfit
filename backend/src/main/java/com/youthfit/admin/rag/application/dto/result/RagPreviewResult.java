package com.youthfit.admin.rag.application.dto.result;

import java.util.List;

public record RagPreviewResult(
        long policyId,
        String query,
        List<String> extractedKeywords,
        PreviewSideResult baseline,
        PreviewSideResult candidate,
        List<RankChangeResult> rankChanges
) {}
