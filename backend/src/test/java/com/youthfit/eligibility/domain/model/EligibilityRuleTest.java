package com.youthfit.eligibility.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EligibilityRule Entity")
class EligibilityRuleTest {

    @Test
    @DisplayName("Builder로 규칙을 생성하면 모든 필드가 설정된다")
    void builder_setsAllFields() {
        // given & when
        EligibilityRule rule = EligibilityRule.builder()
                .policyId(1L)
                .field("age")
                .operator(RuleOperator.BETWEEN)
                .value("19~34")
                .label("연령 요건")
                .sourceReference("자격 요건 > 연령 항목")
                .build();

        // then
        assertThat(rule.getPolicyId()).isEqualTo(1L);
        assertThat(rule.getField()).isEqualTo("age");
        assertThat(rule.getOperator()).isEqualTo(RuleOperator.BETWEEN);
        assertThat(rule.getValue()).isEqualTo("19~34");
        assertThat(rule.getLabel()).isEqualTo("연령 요건");
        assertThat(rule.getSourceReference()).isEqualTo("자격 요건 > 연령 항목");
    }

    @Test
    @DisplayName("sourceReference 없이 규칙을 생성할 수 있다")
    void builder_withoutSourceReference_allowsNull() {
        // given & when
        EligibilityRule rule = EligibilityRule.builder()
                .policyId(1L)
                .field("region")
                .operator(RuleOperator.EQ)
                .value("11")
                .label("거주지")
                .build();

        // then
        assertThat(rule.getSourceReference()).isNull();
    }

    @Test
    @DisplayName("confidence 를 지정하지 않으면 MEDIUM 으로 기본값이 설정된다")
    void builder_confidenceDefaultsToMedium() {
        EligibilityRule rule = EligibilityRule.builder()
                .policyId(1L)
                .field("age")
                .operator(RuleOperator.BETWEEN)
                .value("19~34")
                .label("연령")
                .sourceReference("자격 요건 > 연령")
                .build();

        assertThat(rule.getConfidence()).isEqualTo(RuleConfidence.MEDIUM);
        assertThat(rule.getSourceHash()).isNull();
        assertThat(rule.getExtractionVersion()).isNull();
    }

    @Test
    @DisplayName("confidence 와 추출 메타데이터를 지정하면 그대로 반영된다")
    void builder_confidenceIsRespectedWhenSet() {
        EligibilityRule rule = EligibilityRule.builder()
                .policyId(1L)
                .field("age")
                .operator(RuleOperator.BETWEEN)
                .value("19~34")
                .label("연령")
                .sourceReference("자격 요건 > 연령")
                .confidence(RuleConfidence.LOW)
                .sourceHash("abc")
                .extractionVersion("v1")
                .build();

        assertThat(rule.getConfidence()).isEqualTo(RuleConfidence.LOW);
        assertThat(rule.getSourceHash()).isEqualTo("abc");
        assertThat(rule.getExtractionVersion()).isEqualTo("v1");
    }
}
