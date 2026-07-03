package com.youthfit.eval.dataset;

import java.util.List;

public record EvalDataset(
        int version,
        String embeddingModel,
        List<EvalCase> cases
) {}
