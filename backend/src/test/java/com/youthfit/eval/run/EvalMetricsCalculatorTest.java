package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.eval.dataset.EvalQuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("EvalMetricsCalculator")
class EvalMetricsCalculatorTest {

    private final EvalMetricsCalculator calculator = new EvalMetricsCalculator();

    private EvalCase evalCase(String id, EvalQuestionType type) {
        return new EvalCase(id, 1L, "정책", "질문", type,
                type == EvalQuestionType.NEGATIVE ? List.of() : List.of("스니펫"), null);
    }

    private CaseResult ok(EvalCase c, Integer firstRelevantRank, List<RankedChunk> ranked) {
        return new CaseResult(c, CaseStatus.OK, ranked, firstRelevantRank, 100L, c.question(), null);
    }

    @Test
    @DisplayName("recall@k 와 MRR@10 을 계산한다")
    void calculatesRecallAndMrr() {
        // case1: 1위 정답, case2: 4위 정답, case3: 정답 없음
        CaseResult r1 = ok(evalCase("c1", EvalQuestionType.KEYWORD), 1,
                List.of(new RankedChunk(11L, 1, 0.30, true)));
        CaseResult r2 = ok(evalCase("c2", EvalQuestionType.COLLOQUIAL), 4,
                List.of(new RankedChunk(21L, 1, 0.50, false),
                        new RankedChunk(22L, 2, 0.55, false),
                        new RankedChunk(23L, 3, 0.60, false),
                        new RankedChunk(24L, 4, 0.65, true)));
        CaseResult r3 = ok(evalCase("c3", EvalQuestionType.KEYWORD), null,
                List.of(new RankedChunk(31L, 1, 0.70, false)));

        ScenarioMetrics m = calculator.calculate("baseline", List.of(r1, r2, r3), 0.78);

        assertThat(m.overall().evaluated()).isEqualTo(3);
        assertThat(m.overall().recallAtK().get(1)).isEqualTo(1.0 / 3, within(1e-9));
        assertThat(m.overall().recallAtK().get(3)).isEqualTo(1.0 / 3, within(1e-9));
        assertThat(m.overall().recallAtK().get(5)).isEqualTo(2.0 / 3, within(1e-9));
        assertThat(m.overall().recallAtK().get(10)).isEqualTo(2.0 / 3, within(1e-9));
        // MRR = (1/1 + 1/4 + 0) / 3
        assertThat(m.overall().mrrAt10()).isEqualTo((1.0 + 0.25) / 3, within(1e-9));
        assertThat(m.byType().get(EvalQuestionType.KEYWORD).evaluated()).isEqualTo(2);
        assertThat(m.byType().get(EvalQuestionType.COLLOQUIAL).evaluated()).isEqualTo(1);
    }

    @Test
    @DisplayName("NEGATIVE 오탐률 — top-1 distance 가 threshold 이하면 오탐")
    void calculatesNegativeFalsePositiveRate() {
        EvalCase neg1 = evalCase("n1", EvalQuestionType.NEGATIVE);
        EvalCase neg2 = evalCase("n2", EvalQuestionType.NEGATIVE);
        CaseResult fp = ok(neg1, null, List.of(new RankedChunk(1L, 1, 0.60, false))); // 0.60 <= 0.78 오탐
        CaseResult tn = ok(neg2, null, List.of(new RankedChunk(2L, 1, 0.90, false)));

        ScenarioMetrics m = calculator.calculate("baseline", List.of(fp, tn), 0.78);

        assertThat(m.negativeFalsePositiveRate()).isEqualTo(0.5, within(1e-9));
        assertThat(m.overall().evaluated()).isZero(); // NEGATIVE 는 recall 집계 제외
    }

    @Test
    @DisplayName("distance 갭 — 정답 청크 평균 vs 비정답 top-5 평균")
    void calculatesDistanceGap() {
        CaseResult r = ok(evalCase("c1", EvalQuestionType.KEYWORD), 1,
                List.of(new RankedChunk(1L, 1, 0.40, true),
                        new RankedChunk(2L, 2, 0.70, false),
                        new RankedChunk(3L, 3, 0.80, false)));

        ScenarioMetrics m = calculator.calculate("baseline", List.of(r), 0.78);

        assertThat(m.relevantDistanceAvg()).isEqualTo(0.40, within(1e-9));
        assertThat(m.irrelevantDistanceAvg()).isEqualTo(0.75, within(1e-9));
    }

    @Test
    @DisplayName("OK 아닌 케이스는 모든 지표에서 제외한다")
    void excludesNonOkCases() {
        CaseResult stale = new CaseResult(evalCase("s1", EvalQuestionType.KEYWORD),
                CaseStatus.STALE, List.of(), null, 0L, "질문", null);
        CaseResult okCase = ok(evalCase("c1", EvalQuestionType.KEYWORD), 1,
                List.of(new RankedChunk(1L, 1, 0.40, true)));

        ScenarioMetrics m = calculator.calculate("baseline", List.of(stale, okCase), 0.78);

        assertThat(m.totalCases()).isEqualTo(2);
        assertThat(m.okCases()).isEqualTo(1);
        assertThat(m.overall().evaluated()).isEqualTo(1);
    }
}
