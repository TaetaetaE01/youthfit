package com.youthfit.eval.dataset;

import java.util.List;

public record EvalCase(
        String id,
        Long policyId,
        String policyTitle,
        String question,
        EvalQuestionType questionType,
        List<String> expectedSnippets,
        String notes
) {}
