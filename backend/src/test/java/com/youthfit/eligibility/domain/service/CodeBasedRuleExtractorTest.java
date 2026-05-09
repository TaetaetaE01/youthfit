package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeBasedRuleExtractorTest {

    private final CodeBasedRuleExtractor sut = new CodeBasedRuleExtractor();

    private CodeBasedExtractionInput input(
            Integer ageMin, Integer ageMax, String ageLimitYn,
            String mrgSttsCd, String earnCndSeCd, Integer earnMin, Integer earnMax, String earnEtcCn,
            String jobCd, String schoolCd, String plcyMajorCd, String sbizCd,
            List<String> zipCodes) {
        return new CodeBasedExtractionInput(
                ageMin, ageMax, ageLimitYn,
                mrgSttsCd, earnCndSeCd, earnMin, earnMax, earnEtcCn,
                jobCd, schoolCd, plcyMajorCd, sbizCd,
                zipCodes == null ? List.of() : zipCodes);
    }

    private EligibilityRule findRule(List<EligibilityRule> rules, String field) {
        return rules.stream()
                .filter(r -> r.getField().equals(field))
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule not found: " + field));
    }

    @Test
    void extract_always_returns_8_rules_with_HIGH_confidence_and_codeV1_version() {
        List<EligibilityRule> rules = sut.extract(1L,
                input(null,null,null, null,null,null,null,null,
                      null,null,null,null, List.of()));
        assertThat(rules).hasSize(8);
        assertThat(rules).allSatisfy(r -> {
            assertThat(r.getPolicyId()).isEqualTo(1L);
            assertThat(r.getConfidence()).isEqualTo(RuleConfidence.HIGH);
            assertThat(r.getExtractionVersion()).isEqualTo("code-v1");
        });
    }

    @Test
    void extract_returns_8_distinct_fields_with_correct_order() {
        List<EligibilityRule> rules = sut.extract(1L,
                input(19, 34, "Y", "0055002", "0043002", null, 32_000_000, null,
                      "0013001", "0049007", "0011005", "0014001", List.of("11680")));
        assertThat(rules).extracting(EligibilityRule::getField)
                .containsExactly("age", "maritalStatus", "annualIncome", "employmentKind",
                                 "education", "majorField", "specializationField", "region");
    }

    // ── age ──
    @Test
    void age_returns_ANY_when_ageLimitYn_is_N() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(15, 40, "N", null, null, null, null, null, null, null, null, null, List.of())), "age");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
        assertThat(r.getValue()).isEqualTo("ALL");
        assertThat(r.getLabel()).isEqualTo("연령");
        assertThat(r.getSourceReference()).isEqualTo("getPlcy.sprtTrgtAgeLmtYn: N");
    }

    @Test
    void age_returns_BETWEEN_when_min_and_max_present() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(19, 34, "Y", null, null, null, null, null, null, null, null, null, List.of())), "age");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.BETWEEN);
        assertThat(r.getValue()).isEqualTo("19~34");
    }

    @Test
    void age_returns_GTE_when_only_min_present() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(19, null, "Y", null, null, null, null, null, null, null, null, null, List.of())), "age");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.GTE);
        assertThat(r.getValue()).isEqualTo("19");
    }

    @Test
    void age_returns_LTE_when_only_max_present() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null, 34, "Y", null, null, null, null, null, null, null, null, null, List.of())), "age");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.LTE);
        assertThat(r.getValue()).isEqualTo("34");
    }

    @Test
    void age_returns_ANY_when_all_null() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null, null, null, null, null, null, null, null, null, null, null, null, List.of())), "age");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
    }

    // ── maritalStatus ──
    @Test
    void maritalStatus_returns_EQ_MARRIED_for_0055001() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null,null,null, "0055001", null,null,null,null, null,null,null,null, List.of())), "maritalStatus");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.EQ);
        assertThat(r.getValue()).isEqualTo("MARRIED");
    }

    @Test
    void maritalStatus_returns_EQ_SINGLE_for_0055002() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null,null,null, "0055002", null,null,null,null, null,null,null,null, List.of())), "maritalStatus");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.EQ);
        assertThat(r.getValue()).isEqualTo("SINGLE");
    }

    @Test
    void maritalStatus_returns_ANY_for_0055003_or_null() {
        for (String code : new String[]{"0055003", null}) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, code, null,null,null,null, null,null,null,null, List.of())), "maritalStatus");
            assertThat(r.getOperator()).as("code=" + code).isEqualTo(RuleOperator.ANY);
        }
    }

    // ── annualIncome ──
    @Test
    void annualIncome_returns_ANY_for_0043001_or_null() {
        for (String code : new String[]{"0043001", null}) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, null, code, null,null,null, null,null,null,null, List.of())), "annualIncome");
            assertThat(r.getOperator()).as("code=" + code).isEqualTo(RuleOperator.ANY);
        }
    }

    @Test
    void annualIncome_returns_BETWEEN_for_0043002_with_min_and_max() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null,null,null, null, "0043002", 10_000_000, 32_000_000, null, null,null,null,null, List.of())), "annualIncome");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.BETWEEN);
        assertThat(r.getValue()).isEqualTo("10000000~32000000");
    }

    @Test
    void annualIncome_returns_LTE_for_0043002_with_only_max() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null,null,null, null, "0043002", null, 32_000_000, null, null,null,null,null, List.of())), "annualIncome");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.LTE);
        assertThat(r.getValue()).isEqualTo("32000000");
    }

    @Test
    void annualIncome_returns_ANY_for_0043003_etc() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null,null,null, null, "0043003", null, null,
                      "근로 사업소득 월 10만원 이상", null,null,null,null, List.of())), "annualIncome");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
        assertThat(r.getSourceReference()).contains("근로 사업소득 월 10만원 이상");
    }

    // ── employmentKind ──
    @Test
    void employmentKind_returns_ANY_for_0013010_or_0013009_or_null() {
        for (String code : new String[]{"0013010", "0013009", null}) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, null, null,null,null,null, code, null,null,null, List.of())), "employmentKind");
            assertThat(r.getOperator()).as("code=" + code).isEqualTo(RuleOperator.ANY);
        }
    }

    @Test
    void employmentKind_returns_EQ_for_each_known_code() {
        String[][] cases = {
                {"0013001","EMPLOYEE"},{"0013002","SELF_EMPLOYED"},{"0013003","UNEMPLOYED"},
                {"0013004","FREELANCER"},{"0013005","DAILY_WORKER"},{"0013006","ENTREPRENEUR"},
                {"0013007","PART_TIME"},{"0013008","FARMER"}
        };
        for (String[] c : cases) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, null, null,null,null,null, c[0], null,null,null, List.of())), "employmentKind");
            assertThat(r.getOperator()).as(c[0]).isEqualTo(RuleOperator.EQ);
            assertThat(r.getValue()).as(c[0]).isEqualTo(c[1]);
        }
    }

    // ── education ──
    @Test
    void education_returns_ANY_for_0049010_or_0049009_or_null() {
        for (String code : new String[]{"0049010", "0049009", null}) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, null, null,null,null,null, null, code,null,null, List.of())), "education");
            assertThat(r.getOperator()).as("code=" + code).isEqualTo(RuleOperator.ANY);
        }
    }

    @Test
    void education_returns_EQ_for_each_known_code() {
        String[][] cases = {
                {"0049001","UNDER_HIGH"},{"0049002","HIGH_SCHOOL_IN"},{"0049003","HIGH_SCHOOL_EXPECTED"},
                {"0049004","HIGH_SCHOOL_GRAD"},{"0049005","COLLEGE_IN"},{"0049006","COLLEGE_EXPECTED"},
                {"0049007","COLLEGE_GRAD"},{"0049008","GRADUATE"}
        };
        for (String[] c : cases) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, null, null,null,null,null, null, c[0],null,null, List.of())), "education");
            assertThat(r.getOperator()).as(c[0]).isEqualTo(RuleOperator.EQ);
            assertThat(r.getValue()).as(c[0]).isEqualTo(c[1]);
        }
    }

    // ── majorField ──
    @Test
    void majorField_returns_ANY_for_0011009_or_0011008_or_null() {
        for (String code : new String[]{"0011009", "0011008", null}) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, null, null,null,null,null, null,null, code,null, List.of())), "majorField");
            assertThat(r.getOperator()).as("code=" + code).isEqualTo(RuleOperator.ANY);
        }
    }

    @Test
    void majorField_returns_EQ_for_each_known_code() {
        String[][] cases = {
                {"0011001","HUMANITIES"},{"0011002","SOCIAL"},{"0011003","ECONOMICS"},
                {"0011004","NATURAL"},{"0011005","ENGINEERING"},{"0011006","ARTS"},
                {"0011007","AGRICULTURE"}
        };
        for (String[] c : cases) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, null, null,null,null,null, null,null, c[0],null, List.of())), "majorField");
            assertThat(r.getOperator()).as(c[0]).isEqualTo(RuleOperator.EQ);
            assertThat(r.getValue()).as(c[0]).isEqualTo(c[1]);
        }
    }

    // ── specializationField ──
    @Test
    void specializationField_returns_ANY_for_0014010_or_0014009_or_null() {
        for (String code : new String[]{"0014010", "0014009", null}) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, null, null,null,null,null, null,null,null, code, List.of())), "specializationField");
            assertThat(r.getOperator()).as("code=" + code).isEqualTo(RuleOperator.ANY);
        }
    }

    @Test
    void specializationField_returns_EQ_for_each_known_code() {
        String[][] cases = {
                {"0014001","SME"},{"0014002","WOMAN"},{"0014003","BASIC_LIVELIHOOD"},
                {"0014004","SINGLE_PARENT"},{"0014005","DISABLED"},{"0014006","FARMER"},
                {"0014007","MILITARY"},{"0014008","LOCAL_TALENT"}
        };
        for (String[] c : cases) {
            EligibilityRule r = findRule(sut.extract(1L,
                    input(null,null,null, null, null,null,null,null, null,null,null, c[0], List.of())), "specializationField");
            assertThat(r.getOperator()).as(c[0]).isEqualTo(RuleOperator.EQ);
            assertThat(r.getValue()).as(c[0]).isEqualTo(c[1]);
        }
    }

    // ── region ──
    @Test
    void region_returns_ANY_when_zipCodes_empty() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null,null,null,null, List.of())), "region");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
    }

    @Test
    void region_returns_EQ_SEOUL_when_only_seoul_codes() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null,null,null,null,
                      List.of("11000","11680","11710"))), "region");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.EQ);
        assertThat(r.getValue()).isEqualTo("SEOUL");
    }

    @Test
    void region_returns_IN_when_multiple_sido() {
        EligibilityRule r = findRule(sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null,null,null,null,
                      List.of("11680","26110"))), "region");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.IN);
        assertThat(r.getValue().split(",")).containsExactlyInAnyOrder("SEOUL","BUSAN");
    }

    @Test
    void region_returns_ANY_when_all_17_sido() {
        List<String> all17 = List.of(
            "11000","26000","27000","28000","29000","30000","31000","36000","41000",
            "51000","43000","44000","52000","46000","47000","48000","50000");
        EligibilityRule r = findRule(sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null,null,null,null, all17)), "region");
        assertThat(r.getOperator()).isEqualTo(RuleOperator.ANY);
    }

    @Test
    void region_treats_legacy_prefix_42_as_GANGWON_and_45_as_JEONBUK() {
        EligibilityRule r1 = findRule(sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null,null,null,null,
                      List.of("42000"))), "region");
        assertThat(r1.getOperator()).isEqualTo(RuleOperator.EQ);
        assertThat(r1.getValue()).isEqualTo("GANGWON");

        EligibilityRule r2 = findRule(sut.extract(1L,
                input(null,null,null, null, null,null,null,null, null,null,null,null,
                      List.of("45000"))), "region");
        assertThat(r2.getOperator()).isEqualTo(RuleOperator.EQ);
        assertThat(r2.getValue()).isEqualTo("JEONBUK");
    }
}
