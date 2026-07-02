package com.youthfit.eval.report;

import com.youthfit.eval.run.ScenarioMetrics;
import com.youthfit.rag.application.dto.result.EffectiveConfig;

import java.util.List;

public record ScenarioReport(
        String scenario,
        EffectiveConfig effectiveConfig,
        ScenarioMetrics metrics,
        List<CaseResultRow> cases
) {}
