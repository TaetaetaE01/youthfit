package com.youthfit.admin.rag.application.service;

import com.youthfit.admin.rag.application.dto.result.RankChangeResult;
import com.youthfit.rag.application.dto.result.MergedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RankChangeCalculator {

    public List<RankChangeResult> compute(List<MergedChunk> baseline, List<MergedChunk> candidate) {
        Map<Long, Integer> baseRank = new HashMap<>();
        for (MergedChunk c : baseline) baseRank.put(c.chunkId(), c.rank());
        Map<Long, Integer> candRank = new HashMap<>();
        for (MergedChunk c : candidate) candRank.put(c.chunkId(), c.rank());

        List<RankChangeResult> out = new ArrayList<>();
        // moved + dropped
        for (Map.Entry<Long, Integer> e : baseRank.entrySet()) {
            Integer cand = candRank.get(e.getKey());
            if (cand == null) {
                out.add(RankChangeResult.dropped(e.getKey(), e.getValue()));
            } else if (!cand.equals(e.getValue())) {
                out.add(RankChangeResult.moved(e.getKey(), e.getValue(), cand));
            }
        }
        // new
        for (Map.Entry<Long, Integer> e : candRank.entrySet()) {
            if (!baseRank.containsKey(e.getKey())) {
                out.add(RankChangeResult.newcomer(e.getKey(), e.getValue()));
            }
        }
        return out;
    }
}
