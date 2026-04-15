package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CriterionEvaluation")
class CriterionEvaluationTest {

    @Test
    @DisplayName("eligible 팩토리 - LIKELY_ELIGIBLE 결과와 충족 사유를 생성한다")
    void eligible_createsEligibleResult() {
        // given
        EligibilityRule rule = createRule("age", RuleOperator.BETWEEN, "19~34", "연령");

        // when
        CriterionEvaluation result = CriterionEvaluation.eligible(rule, 25);

        // then
        assertThat(result.field()).isEqualTo("age");
        assertThat(result.label()).isEqualTo("연령");
        assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        assertThat(result.reason()).contains("25").contains("충족");
        assertThat(result.sourceReference()).isEqualTo("자격 요건 > 연령 항목");
    }

    @Test
    @DisplayName("ineligible 팩토리 - LIKELY_INELIGIBLE 결과와 미충족 사유를 생성한다")
    void ineligible_createsIneligibleResult() {
        // given
        EligibilityRule rule = createRule("age", RuleOperator.BETWEEN, "19~34", "연령");

        // when
        CriterionEvaluation result = CriterionEvaluation.ineligible(rule, 40);

        // then
        assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        assertThat(result.reason()).contains("40").contains("미충족");
    }

    @Test
    @DisplayName("uncertain 팩토리 - UNCERTAIN 결과와 정보 미입력 사유를 생성한다")
    void uncertain_createsUncertainResult() {
        // given
        EligibilityRule rule = createRule("age", RuleOperator.BETWEEN, "19~34", "연령");

        // when
        CriterionEvaluation result = CriterionEvaluation.uncertain(rule);

        // then
        assertThat(result.result()).isEqualTo(EligibilityResult.UNCERTAIN);
        assertThat(result.reason()).contains("정보 미입력");
    }

    @Test
    @DisplayName("eligible 팩토리 - 문자열 값도 포맷팅된다")
    void eligible_formatsStringValue() {
        // given
        EligibilityRule rule = createRule("region", RuleOperator.EQ, "11", "거주지");

        // when
        CriterionEvaluation result = CriterionEvaluation.eligible(rule, "서울");

        // then
        assertThat(result.reason()).contains("서울");
    }

    private EligibilityRule createRule(String field, RuleOperator operator, String value, String label) {
        return EligibilityRule.builder()
                .policyId(1L)
                .field(field)
                .operator(operator)
                .value(value)
                .label(label)
                .sourceReference("자격 요건 > " + label + " 항목")
                .build();
    }
}
