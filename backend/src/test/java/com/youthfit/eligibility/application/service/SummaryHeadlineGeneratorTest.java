package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.view.SummaryView;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryHeadlineGenerator")
class SummaryHeadlineGeneratorTest {

    private final SummaryHeadlineGenerator generator = new SummaryHeadlineGenerator();

    @Test
    @DisplayName("미충족 1개 → \"{label} 1개 조건이 맞지 않아요\"")
    void singleIneligible() {
        SummaryView view = generator.generate(List.of(
                ineligibleEval("employmentKind", "고용 형태"),
                eligibleEval("age", "연령")
        ));

        assertThat(view.headline()).isEqualTo("고용 형태 1개 조건이 맞지 않아요");
        assertThat(view.eligibleCount()).isEqualTo(1);
        assertThat(view.uncertainCount()).isZero();
        assertThat(view.ineligibleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("미충족 2개 이상 → \"{대표 label} 등 N개 조건이 맞지 않아요\"")
    void multipleIneligible() {
        SummaryView view = generator.generate(List.of(
                ineligibleEval("age", "연령"),
                ineligibleEval("employmentKind", "고용 형태"),
                eligibleEval("region", "거주지")
        ));

        assertThat(view.headline()).isEqualTo("연령 등 2개 조건이 맞지 않아요");
        assertThat(view.ineligibleCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("미충족 0 + 미입력 N개 → \"{대표 label} 등 N개 정보가 더 필요해요\"")
    void onlyMissingFields() {
        SummaryView view = generator.generate(List.of(
                eligibleEval("age", "연령"),
                missingEval("annualIncome", "가구 소득"),
                missingEval("education", "학력")
        ));

        assertThat(view.headline()).isEqualTo("가구 소득 등 2개 정보가 더 필요해요");
        assertThat(view.uncertainCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("미충족 0 + 모호 N개 → \"정책 원문이 모호한 조건이 N개 있어요\"")
    void onlyAmbiguous() {
        SummaryView view = generator.generate(List.of(
                eligibleEval("age", "연령"),
                ambiguousEval("specializationField", "특화 분야")
        ));

        assertThat(view.headline()).isEqualTo("정책 원문이 모호한 조건이 1개 있어요");
    }

    @Test
    @DisplayName("모두 통과 → \"모든 조건을 충족해요\"")
    void allEligible() {
        SummaryView view = generator.generate(List.of(
                eligibleEval("age", "연령"),
                eligibleEval("region", "거주지")
        ));

        assertThat(view.headline()).isEqualTo("모든 조건을 충족해요");
        assertThat(view.eligibleCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("미입력 1개만 있을 때 → \"{label} 정보가 더 필요해요\" (등 N개 표현 없음)")
    void singleMissing() {
        SummaryView view = generator.generate(List.of(
                missingEval("annualIncome", "가구 소득")
        ));

        assertThat(view.headline()).isEqualTo("가구 소득 정보가 더 필요해요");
    }

    private CriterionEvaluation eligibleEval(String field, String label) {
        return CriterionEvaluation.eligible(makeRule(field, label), "value");
    }

    private CriterionEvaluation ineligibleEval(String field, String label) {
        return CriterionEvaluation.ineligible(makeRule(field, label), "value");
    }

    private CriterionEvaluation missingEval(String field, String label) {
        return CriterionEvaluation.uncertain(makeRule(field, label));
    }

    private CriterionEvaluation ambiguousEval(String field, String label) {
        return CriterionEvaluation.lowConfidenceUncertain(makeRule(field, label));
    }

    private EligibilityRule makeRule(String field, String label) {
        return EligibilityRule.builder()
                .policyId(1L).field(field).operator(RuleOperator.EQ)
                .value("v").label(label).confidence(RuleConfidence.HIGH).build();
    }
}
