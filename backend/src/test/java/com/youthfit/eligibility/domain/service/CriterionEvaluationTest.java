package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.UncertainReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CriterionEvaluation")
class CriterionEvaluationTest {

    private final EligibilityRule rule = EligibilityRule.builder()
            .policyId(1L).field("age").operator(RuleOperator.BETWEEN)
            .value("19~34").label("연령").sourceReference("자격 요건 > 연령 항목")
            .confidence(RuleConfidence.HIGH).build();

    @Test
    @DisplayName("eligible 헬퍼는 LIKELY_ELIGIBLE에 userValue를 보존한다")
    void eligibleHelper() {
        CriterionEvaluation eval = CriterionEvaluation.eligible(rule, 29);

        assertThat(eval.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        assertThat(eval.userValue()).isEqualTo(29);
        assertThat(eval.uncertainReason()).isNull();
        assertThat(eval.field()).isEqualTo("age");
        assertThat(eval.label()).isEqualTo("연령");
    }

    @Test
    @DisplayName("ineligible 헬퍼는 LIKELY_INELIGIBLE에 userValue를 보존한다")
    void ineligibleHelper() {
        CriterionEvaluation eval = CriterionEvaluation.ineligible(rule, 35);

        assertThat(eval.result()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        assertThat(eval.userValue()).isEqualTo(35);
        assertThat(eval.uncertainReason()).isNull();
    }

    @Test
    @DisplayName("uncertain 헬퍼는 MISSING_FIELD 사유를 갖는다")
    void uncertainHelper() {
        CriterionEvaluation eval = CriterionEvaluation.uncertain(rule);

        assertThat(eval.result()).isEqualTo(EligibilityResult.UNCERTAIN);
        assertThat(eval.userValue()).isNull();
        assertThat(eval.uncertainReason()).isEqualTo(UncertainReason.MISSING_FIELD);
    }

    @Test
    @DisplayName("lowConfidenceUncertain 헬퍼는 AMBIGUOUS_SOURCE 사유를 갖는다")
    void lowConfidenceUncertainHelper() {
        CriterionEvaluation eval = CriterionEvaluation.lowConfidenceUncertain(rule);

        assertThat(eval.result()).isEqualTo(EligibilityResult.UNCERTAIN);
        assertThat(eval.uncertainReason()).isEqualTo(UncertainReason.AMBIGUOUS_SOURCE);
    }
}
