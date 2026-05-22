package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.admin.rag.application.dto.result.RankChangeResult;

public record RankChangeResponse(
        long chunkId,
        Integer baselineRank,
        Integer candidateRank,
        String delta   // "NEW" | "DROPPED" | 정수("+N"/"-N"/"0")
) {
    public static RankChangeResponse from(RankChangeResult r) {
        String d;
        if (r.baselineRank() == null) d = "NEW";
        else if (r.candidateRank() == null) d = "DROPPED";
        else d = (r.delta() > 0 ? "+" : "") + r.delta();
        return new RankChangeResponse(r.chunkId(), r.baselineRank(), r.candidateRank(), d);
    }
}
