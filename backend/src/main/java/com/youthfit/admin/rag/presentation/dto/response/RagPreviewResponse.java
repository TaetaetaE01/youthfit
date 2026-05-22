package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.admin.rag.application.dto.result.RagPreviewResult;

import java.util.List;

public record RagPreviewResponse(
        long policyId,
        String query,
        List<String> extractedKeywords,
        PreviewSideResponse baseline,
        PreviewSideResponse candidate,
        DiffResponse diff
) {
    public record DiffResponse(List<RankChangeResponse> rankChanges) {}

    public static RagPreviewResponse from(RagPreviewResult r) {
        return new RagPreviewResponse(
                r.policyId(),
                r.query(),
                r.extractedKeywords(),
                PreviewSideResponse.from(r.baseline()),
                PreviewSideResponse.from(r.candidate()),
                new DiffResponse(r.rankChanges().stream()
                        .map(RankChangeResponse::from).toList()));
    }
}
