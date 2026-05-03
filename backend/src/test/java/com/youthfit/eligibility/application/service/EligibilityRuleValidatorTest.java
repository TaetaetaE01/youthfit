package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.application.service.EligibilityRuleValidator.ValidationReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityRuleValidatorTest {

    private final EligibilityRuleValidator validator = new EligibilityRuleValidator();

    @Test
    void allowed_age_between_passes() {
        RawExtractedRule rule = new RawExtractedRule(
                "age", "BETWEEN", "19~34", "연령", "자격 > 연령", "HIGH");
        ValidationReport report = validator.validate(List.of(rule));

        assertThat(report.acceptedRules()).hasSize(1);
        assertThat(report.acceptedRules().get(0).field()).isEqualTo("age");
        assertThat(report.feedbackMessages()).isEmpty();
        assertThat(report.shouldRetry()).isFalse();
    }

    @Test
    void unknown_field_is_rejected() {
        RawExtractedRule rule = new RawExtractedRule(
                "householdSize", "EQ", "1", "가구원수", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(rule));

        assertThat(report.acceptedRules()).isEmpty();
        assertThat(report.feedbackMessages()).anyMatch(m -> m.contains("householdSize"));
    }

    @Test
    void unknown_operator_is_rejected() {
        RawExtractedRule rule = new RawExtractedRule(
                "age", "MOD", "2", "연령", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(rule));

        assertThat(report.acceptedRules()).isEmpty();
        assertThat(report.feedbackMessages()).anyMatch(m -> m.contains("MOD"));
    }

    @Test
    void age_between_invalid_format_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "age", "BETWEEN", "19-34", "연령", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
        assertThat(report.feedbackMessages()).anyMatch(m -> m.contains("BETWEEN"));
    }

    @Test
    void age_between_min_greater_than_max_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "age", "BETWEEN", "40~30", "연령", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void age_outside_zero_to_hundred_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "age", "GTE", "150", "연령", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void negative_income_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "annualIncome", "LTE", "-1000", "연소득", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void marital_status_invalid_enum_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "maritalStatus", "EQ", "DIVORCED", "혼인", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void marital_status_valid_enum_passes() {
        RawExtractedRule ok = new RawExtractedRule(
                "maritalStatus", "EQ", "SINGLE", "혼인", "원문", "HIGH");
        ValidationReport report = validator.validate(List.of(ok));

        assertThat(report.acceptedRules()).hasSize(1);
    }

    @Test
    void employment_kind_in_with_comma_passes_when_all_enum() {
        RawExtractedRule ok = new RawExtractedRule(
                "employmentKind", "IN", "EMPLOYEE,FREELANCER", "고용", "원문", "MEDIUM");
        ValidationReport report = validator.validate(List.of(ok));

        assertThat(report.acceptedRules()).hasSize(1);
    }

    @Test
    void employment_kind_in_with_one_invalid_member_is_rejected() {
        RawExtractedRule bad = new RawExtractedRule(
                "employmentKind", "IN", "EMPLOYEE,WIZARD", "고용", "원문", "MEDIUM");
        ValidationReport report = validator.validate(List.of(bad));

        assertThat(report.acceptedRules()).isEmpty();
    }

    @Test
    void unknown_confidence_is_corrected_to_medium() {
        RawExtractedRule rule = new RawExtractedRule(
                "age", "BETWEEN", "19~34", "연령", "원문", "TOTALLY_SURE");
        ValidationReport report = validator.validate(List.of(rule));

        assertThat(report.acceptedRules()).hasSize(1);
        assertThat(report.acceptedRules().get(0).confidence()).isEqualTo("MEDIUM");
    }

    @Test
    void retry_triggered_when_more_than_half_dropped() {
        RawExtractedRule ok1 = new RawExtractedRule("age", "BETWEEN", "19~34", "연령", "원문", "HIGH");
        RawExtractedRule bad1 = new RawExtractedRule("householdSize", "EQ", "1", "가구원수", "원문", "HIGH");
        RawExtractedRule bad2 = new RawExtractedRule("creditScore", "GTE", "700", "신용", "원문", "HIGH");

        ValidationReport report = validator.validate(List.of(ok1, bad1, bad2));

        assertThat(report.acceptedRules()).hasSize(1);
        assertThat(report.shouldRetry()).isTrue();
    }

    @Test
    void retry_not_triggered_when_majority_pass() {
        RawExtractedRule ok1 = new RawExtractedRule("age", "BETWEEN", "19~34", "연령", "원문", "HIGH");
        RawExtractedRule ok2 = new RawExtractedRule("region", "EQ", "SEOUL", "거주", "원문", "HIGH");
        RawExtractedRule bad1 = new RawExtractedRule("householdSize", "EQ", "1", "가구원수", "원문", "HIGH");

        ValidationReport report = validator.validate(List.of(ok1, ok2, bad1));

        assertThat(report.acceptedRules()).hasSize(2);
        assertThat(report.shouldRetry()).isFalse();
    }

    @Test
    void empty_input_does_not_trigger_retry() {
        ValidationReport report = validator.validate(List.of());

        assertThat(report.acceptedRules()).isEmpty();
        assertThat(report.shouldRetry()).isFalse();
    }
}
