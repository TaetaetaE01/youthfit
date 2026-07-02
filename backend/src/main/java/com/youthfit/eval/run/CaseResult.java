package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.rag.application.dto.result.EffectiveConfig;

import java.util.List;

public record CaseResult(
        EvalCase evalCase,
        CaseStatus status,
        List<RankedChunk> ranked,
        Integer firstRelevantRank,
        long tookMs,
        String effectiveQuestion,
        EffectiveConfig effective
) {}
