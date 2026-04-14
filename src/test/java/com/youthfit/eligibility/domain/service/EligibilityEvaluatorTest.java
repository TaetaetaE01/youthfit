package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.user.domain.model.EmploymentStatus;
import com.youthfit.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EligibilityEvaluator")
class EligibilityEvaluatorTest {

    private final EligibilityEvaluator evaluator = new EligibilityEvaluator();

    @Nested
    @DisplayName("BETWEEN 연산자")
    class BetweenOperator {

        @Test
        @DisplayName("범위 내 값이면 LIKELY_ELIGIBLE을 반환한다")
        void withinRange_returnsEligible() {
            User user = createUserWithAge(29);
            EligibilityRule rule = createRule("age", RuleOperator.BETWEEN, "19~34", "연령");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        }

        @Test
        @DisplayName("범위 경계 값이면 LIKELY_ELIGIBLE을 반환한다")
        void boundary_returnsEligible() {
            User user = createUserWithAge(19);
            EligibilityRule rule = createRule("age", RuleOperator.BETWEEN, "19~34", "연령");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        }

        @Test
        @DisplayName("범위 밖 값이면 LIKELY_INELIGIBLE을 반환한다")
        void outOfRange_returnsIneligible() {
            User user = createUserWithAge(35);
            EligibilityRule rule = createRule("age", RuleOperator.BETWEEN, "19~34", "연령");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        }
    }

    @Nested
    @DisplayName("EQ 연산자")
    class EqOperator {

        @Test
        @DisplayName("값이 같으면 LIKELY_ELIGIBLE을 반환한다")
        void equal_returnsEligible() {
            User user = createUserWithRegion("11");
            EligibilityRule rule = createRule("region", RuleOperator.EQ, "11", "거주지");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        }

        @Test
        @DisplayName("값이 다르면 LIKELY_INELIGIBLE을 반환한다")
        void notEqual_returnsIneligible() {
            User user = createUserWithRegion("26");
            EligibilityRule rule = createRule("region", RuleOperator.EQ, "11", "거주지");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        }
    }

    @Nested
    @DisplayName("GTE / LTE 연산자")
    class ComparisonOperator {

        @Test
        @DisplayName("GTE - 값이 기준 이상이면 LIKELY_ELIGIBLE을 반환한다")
        void gte_aboveThreshold_returnsEligible() {
            User user = createUserWithAnnualIncome(30000000L);
            EligibilityRule rule = createRule("annualIncome", RuleOperator.GTE, "20000000", "소득 하한");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        }

        @Test
        @DisplayName("LTE - 값이 기준 이하이면 LIKELY_ELIGIBLE을 반환한다")
        void lte_belowThreshold_returnsEligible() {
            User user = createUserWithAnnualIncome(30000000L);
            EligibilityRule rule = createRule("annualIncome", RuleOperator.LTE, "50000000", "소득 상한");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        }

        @Test
        @DisplayName("LTE - 값이 기준 초과이면 LIKELY_INELIGIBLE을 반환한다")
        void lte_aboveThreshold_returnsIneligible() {
            User user = createUserWithAnnualIncome(60000000L);
            EligibilityRule rule = createRule("annualIncome", RuleOperator.LTE, "50000000", "소득 상한");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        }
    }

    @Nested
    @DisplayName("IN 연산자")
    class InOperator {

        @Test
        @DisplayName("값이 목록에 포함되면 LIKELY_ELIGIBLE을 반환한다")
        void contained_returnsEligible() {
            User user = createUserWithEmploymentStatus(EmploymentStatus.UNEMPLOYED);
            EligibilityRule rule = createRule("employmentStatus", RuleOperator.IN, "UNEMPLOYED,STUDENT", "고용 상태");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        }

        @Test
        @DisplayName("값이 목록에 미포함이면 LIKELY_INELIGIBLE을 반환한다")
        void notContained_returnsIneligible() {
            User user = createUserWithEmploymentStatus(EmploymentStatus.EMPLOYED);
            EligibilityRule rule = createRule("employmentStatus", RuleOperator.IN, "UNEMPLOYED,STUDENT", "고용 상태");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        }
    }

    @Nested
    @DisplayName("NOT_EQ 연산자")
    class NotEqOperator {

        @Test
        @DisplayName("값이 다르면 LIKELY_ELIGIBLE을 반환한다")
        void different_returnsEligible() {
            User user = createUserWithRegion("11");
            EligibilityRule rule = createRule("region", RuleOperator.NOT_EQ, "99", "거주지 제외");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
        }
    }

    @Nested
    @DisplayName("필드값 누락")
    class NullField {

        @Test
        @DisplayName("필드값이 null이면 UNCERTAIN을 반환한다")
        void nullField_returnsUncertain() {
            User user = createBaseUser();
            EligibilityRule rule = createRule("age", RuleOperator.BETWEEN, "19~34", "연령");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.UNCERTAIN);
            assertThat(result.field()).isEqualTo("age");
            assertThat(result.reason()).contains("정보 미입력");
        }

        @Test
        @DisplayName("알 수 없는 필드명이면 UNCERTAIN을 반환한다")
        void unknownField_returnsUncertain() {
            User user = createUserWithAge(29);
            EligibilityRule rule = createRule("unknownField", RuleOperator.EQ, "value", "알 수 없는 기준");

            CriterionEvaluation result = evaluator.evaluateRule(rule, user);

            assertThat(result.result()).isEqualTo(EligibilityResult.UNCERTAIN);
        }
    }

    // ── 헬퍼 메서드 ──

    private User createBaseUser() {
        User user = User.builder()
                .email("test@test.com")
                .nickname("테스터")
                .authProvider(com.youthfit.user.domain.model.AuthProvider.KAKAO)
                .providerId("12345")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private User createUserWithAge(int age) {
        User user = createBaseUser();
        ReflectionTestUtils.setField(user, "age", age);
        return user;
    }

    private User createUserWithRegion(String region) {
        User user = createBaseUser();
        ReflectionTestUtils.setField(user, "region", region);
        return user;
    }

    private User createUserWithAnnualIncome(Long income) {
        User user = createBaseUser();
        ReflectionTestUtils.setField(user, "annualIncome", income);
        return user;
    }

    private User createUserWithEmploymentStatus(EmploymentStatus status) {
        User user = createBaseUser();
        ReflectionTestUtils.setField(user, "employmentStatus", status);
        return user;
    }

    private EligibilityRule createRule(String field, RuleOperator operator, String value, String label) {
        EligibilityRule rule = EligibilityRule.builder()
                .policyId(1L)
                .field(field)
                .operator(operator)
                .value(value)
                .label(label)
                .sourceReference("자격 요건 > " + label + " 항목")
                .build();
        ReflectionTestUtils.setField(rule, "id", 1L);
        return rule;
    }
}
