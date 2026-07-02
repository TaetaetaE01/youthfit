package com.youthfit.eval.run;

import java.util.Map;

public record TypeMetrics(int evaluated, Map<Integer, Double> recallAtK, double mrrAt10) {}
