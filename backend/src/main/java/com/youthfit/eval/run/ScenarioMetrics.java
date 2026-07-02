package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalQuestionType;

import java.util.Map;

public record ScenarioMetrics(
        String scenario,
        int totalCases,
        int okCases,
        TypeMetrics overall,
        Map<EvalQuestionType, TypeMetrics> byType,
        Double relevantDistanceAvg,
        Double irrelevantDistanceAvg,
        Double negativeFalsePositiveRate,
        double avgTookMs
) {}
