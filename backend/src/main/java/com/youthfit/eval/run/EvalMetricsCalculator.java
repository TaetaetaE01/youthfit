package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalQuestionType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시나리오 하나의 CaseResult 리스트에서 집계 지표를 계산한다.
 * recall@k·MRR 은 KEYWORD+COLLOQUIAL(OK) 만, NEGATIVE 는 오탐률 전용.
 */
public class EvalMetricsCalculator {

    private static final List<Integer> KS = List.of(1, 3, 5, 10);

    public ScenarioMetrics calculate(String scenario, List<CaseResult> results, double negativeThreshold) {
        List<CaseResult> ok = results.stream().filter(r -> r.status() == CaseStatus.OK).toList();

        List<CaseResult> positives = ok.stream()
                .filter(r -> r.evalCase().questionType() != EvalQuestionType.NEGATIVE)
                .toList();
        List<CaseResult> negatives = ok.stream()
                .filter(r -> r.evalCase().questionType() == EvalQuestionType.NEGATIVE)
                .toList();

        Map<EvalQuestionType, TypeMetrics> byType = new EnumMap<>(EvalQuestionType.class);
        for (EvalQuestionType type : List.of(EvalQuestionType.KEYWORD, EvalQuestionType.COLLOQUIAL)) {
            List<CaseResult> ofType = positives.stream()
                    .filter(r -> r.evalCase().questionType() == type)
                    .toList();
            if (!ofType.isEmpty()) {
                byType.put(type, typeMetrics(ofType));
            }
        }

        Double negativeFpRate = negatives.isEmpty() ? null
                : negatives.stream()
                        .filter(r -> !r.ranked().isEmpty()
                                && r.ranked().get(0).distance() <= negativeThreshold)
                        .count() / (double) negatives.size();

        List<Double> relevantDistances = positives.stream()
                .flatMap(r -> r.ranked().stream())
                .filter(RankedChunk::relevant)
                .map(RankedChunk::distance)
                .toList();
        List<Double> irrelevantDistances = positives.stream()
                .flatMap(r -> r.ranked().stream().filter(c -> !c.relevant() && c.rank() <= 5))
                .map(RankedChunk::distance)
                .toList();

        double avgTookMs = ok.isEmpty() ? 0.0
                : ok.stream().mapToLong(CaseResult::tookMs).average().orElse(0.0);

        return new ScenarioMetrics(
                scenario,
                results.size(),
                ok.size(),
                typeMetrics(positives),
                byType,
                average(relevantDistances),
                average(irrelevantDistances),
                negativeFpRate,
                avgTookMs
        );
    }

    private TypeMetrics typeMetrics(List<CaseResult> results) {
        Map<Integer, Double> recallAtK = new LinkedHashMap<>();
        for (int k : KS) {
            final int kk = k;
            double recall = results.isEmpty() ? 0.0
                    : results.stream()
                            .filter(r -> r.firstRelevantRank() != null && r.firstRelevantRank() <= kk)
                            .count() / (double) results.size();
            recallAtK.put(k, recall);
        }
        double mrr = results.isEmpty() ? 0.0
                : results.stream()
                        .mapToDouble(r -> r.firstRelevantRank() == null ? 0.0 : 1.0 / r.firstRelevantRank())
                        .average().orElse(0.0);
        return new TypeMetrics(results.size(), recallAtK, mrr);
    }

    private Double average(List<Double> values) {
        return values.isEmpty() ? null
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
