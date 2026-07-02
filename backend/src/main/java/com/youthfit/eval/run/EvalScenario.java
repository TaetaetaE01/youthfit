package com.youthfit.eval.run;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;

/**
 * 평가 시나리오. overrides == null 이면 운영 baseline(yml) 그대로.
 * queryRewrite == true 면 검색 전에 QueryRewriter 로 질문을 재작성한다.
 */
public record EvalScenario(String name, HybridSearchOverrides overrides, boolean queryRewrite) {

    public static EvalScenario of(String name) {
        return switch (name) {
            case "baseline" -> new EvalScenario("baseline", null, false);
            case "hybrid-on" -> new EvalScenario("hybrid-on",
                    new HybridSearchOverrides(true, null, null, null, null, null), false);
            case "boost-off" -> new EvalScenario("boost-off",
                    new HybridSearchOverrides(null, null, null, null, false, null), false);
            case "rewrite-on" -> new EvalScenario("rewrite-on", null, true);
            default -> throw new IllegalArgumentException("알 수 없는 시나리오: " + name
                    + " (지원: baseline, hybrid-on, boost-off, rewrite-on)");
        };
    }
}
