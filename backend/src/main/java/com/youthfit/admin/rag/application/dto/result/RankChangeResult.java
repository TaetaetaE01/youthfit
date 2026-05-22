package com.youthfit.admin.rag.application.dto.result;

/**
 * baselineRank/candidateRank 중 한쪽이 null 이면 NEW/DROPPED.
 * delta: candidateRank - baselineRank (음수=상승, 양수=하락). 한쪽 null 이면 null.
 */
public record RankChangeResult(
        long chunkId,
        Integer baselineRank,
        Integer candidateRank,
        Integer delta
) {
    public static RankChangeResult newcomer(long chunkId, int candidateRank) {
        return new RankChangeResult(chunkId, null, candidateRank, null);
    }
    public static RankChangeResult dropped(long chunkId, int baselineRank) {
        return new RankChangeResult(chunkId, baselineRank, null, null);
    }
    public static RankChangeResult moved(long chunkId, int baselineRank, int candidateRank) {
        return new RankChangeResult(chunkId, baselineRank, candidateRank,
                candidateRank - baselineRank);
    }
}
