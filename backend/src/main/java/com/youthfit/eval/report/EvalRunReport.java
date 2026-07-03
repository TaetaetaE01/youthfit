package com.youthfit.eval.report;

import java.util.List;

public record EvalRunReport(
        String label,
        String executedAt,
        String datasetPath,
        int datasetVersion,
        List<ScenarioReport> scenarios
) {}
