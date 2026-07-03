package com.youthfit.eval.report;

import com.youthfit.eval.run.CaseResult;

public record CaseResultRow(
        String caseId,
        String status,
        String questionType,
        String effectiveQuestion,
        Integer firstRelevantRank,
        Double top1Distance,
        long tookMs
) {
    public static CaseResultRow from(CaseResult r) {
        return new CaseResultRow(
                r.evalCase().id(),
                r.status().name(),
                r.evalCase().questionType().name(),
                r.effectiveQuestion(),
                r.firstRelevantRank(),
                r.ranked().isEmpty() ? null : r.ranked().get(0).distance(),
                r.tookMs()
        );
    }
}
