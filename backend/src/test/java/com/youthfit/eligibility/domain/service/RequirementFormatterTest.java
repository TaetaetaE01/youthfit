package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequirementFormatter")
class RequirementFormatterTest {

    private final RequirementFormatter formatter = new RequirementFormatter();

    @Nested
    @DisplayName("BETWEEN 연산자")
    class Between {

        @Test
        @DisplayName("age 필드 BETWEEN 19~34 → \"만 19세 이상 34세 이하\"")
        void ageBetween() {
            RequirementView view = formatter.format("age", RuleOperator.BETWEEN, "19~34");

            assertThat(view.operator()).isEqualTo("BETWEEN");
            assertThat(view.displayText()).isEqualTo("만 19세 이상 34세 이하");
        }
    }

    @Nested
    @DisplayName("EQ 연산자")
    class Eq {

        @Test
        @DisplayName("maritalStatus EQ MARRIED → \"기혼\"")
        void maritalEq() {
            RequirementView view = formatter.format("maritalStatus", RuleOperator.EQ, "MARRIED");
            assertThat(view.displayText()).isEqualTo("기혼");
        }

        @Test
        @DisplayName("region EQ 1100000000 → 그대로 코드 표시")
        void regionEq() {
            RequirementView view = formatter.format("region", RuleOperator.EQ, "1100000000");
            assertThat(view.displayText()).isEqualTo("1100000000");
        }
    }

    @Nested
    @DisplayName("NOT_EQ 연산자")
    class NotEq {

        @Test
        @DisplayName("maritalStatus NOT_EQ MARRIED → \"기혼 제외\"")
        void notEqEnum() {
            RequirementView view = formatter.format("maritalStatus", RuleOperator.NOT_EQ, "MARRIED");
            assertThat(view.displayText()).isEqualTo("기혼 제외");
        }
    }

    @Nested
    @DisplayName("IN 연산자")
    class In {

        @Test
        @DisplayName("employmentKind IN 다중 enum → 한국어 라벨 콤마 결합")
        void employmentIn() {
            RequirementView view = formatter.format(
                    "employmentKind",
                    RuleOperator.IN,
                    "EMPLOYEE,SELF_EMPLOYED,FREELANCER,DAILY_WORKER,PART_TIME"
            );
            assertThat(view.displayText())
                    .isEqualTo("재직자, 자영업자, 프리랜서, 일용근로자, 단기근로자");
        }

        @Test
        @DisplayName("specializationField IN 다중 enum → 한국어 라벨")
        void specializationIn() {
            RequirementView view = formatter.format(
                    "specializationField",
                    RuleOperator.IN,
                    "SME,WOMAN"
            );
            assertThat(view.displayText()).isEqualTo("중소기업, 여성");
        }
    }

    @Nested
    @DisplayName("GTE / LTE 연산자")
    class Comparison {

        @Test
        @DisplayName("annualIncome GTE 30000000 → \"3,000만원 이상\"")
        void annualIncomeGte() {
            RequirementView view = formatter.format(
                    "annualIncome",
                    RuleOperator.GTE,
                    "30000000"
            );
            assertThat(view.displayText()).isEqualTo("3,000만원 이상");
        }

        @Test
        @DisplayName("annualIncome LTE 50000000 → \"5,000만원 이하\"")
        void annualIncomeLte() {
            RequirementView view = formatter.format(
                    "annualIncome",
                    RuleOperator.LTE,
                    "50000000"
            );
            assertThat(view.displayText()).isEqualTo("5,000만원 이하");
        }

        @Test
        @DisplayName("age GTE 19 → \"만 19세 이상\"")
        void ageGte() {
            RequirementView view = formatter.format("age", RuleOperator.GTE, "19");
            assertThat(view.displayText()).isEqualTo("만 19세 이상");
        }
    }

    @Nested
    @DisplayName("BETWEEN 소득")
    class BetweenIncome {

        @Test
        @DisplayName("annualIncome BETWEEN 20000000~50000000 → \"2,000만원 이상 5,000만원 이하\"")
        void incomeBetween() {
            RequirementView view = formatter.format(
                    "annualIncome",
                    RuleOperator.BETWEEN,
                    "20000000~50000000"
            );
            assertThat(view.displayText()).isEqualTo("2,000만원 이상 5,000만원 이하");
        }
    }
}
