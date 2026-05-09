package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class CodeBasedRuleExtractor {

    public static final String EXTRACTION_VERSION = "code-v1";

    private static final Map<String, String> JOB_CODE_TO_ENUM = Map.of(
            "0013001", "EMPLOYEE",
            "0013002", "SELF_EMPLOYED",
            "0013003", "UNEMPLOYED",
            "0013004", "FREELANCER",
            "0013005", "DAILY_WORKER",
            "0013006", "ENTREPRENEUR",
            "0013007", "PART_TIME",
            "0013008", "FARMER"
    );

    private static final Map<String, String> SCHOOL_CODE_TO_ENUM = Map.ofEntries(
            Map.entry("0049001", "UNDER_HIGH"),
            Map.entry("0049002", "HIGH_SCHOOL_IN"),
            Map.entry("0049003", "HIGH_SCHOOL_EXPECTED"),
            Map.entry("0049004", "HIGH_SCHOOL_GRAD"),
            Map.entry("0049005", "COLLEGE_IN"),
            Map.entry("0049006", "COLLEGE_EXPECTED"),
            Map.entry("0049007", "COLLEGE_GRAD"),
            Map.entry("0049008", "GRADUATE")
    );

    private static final Map<String, String> MAJOR_CODE_TO_ENUM = Map.of(
            "0011001", "HUMANITIES",
            "0011002", "SOCIAL",
            "0011003", "ECONOMICS",
            "0011004", "NATURAL",
            "0011005", "ENGINEERING",
            "0011006", "ARTS",
            "0011007", "AGRICULTURE"
    );

    private static final Map<String, String> SBIZ_CODE_TO_ENUM = Map.ofEntries(
            Map.entry("0014001", "SME"),
            Map.entry("0014002", "WOMAN"),
            Map.entry("0014003", "BASIC_LIVELIHOOD"),
            Map.entry("0014004", "SINGLE_PARENT"),
            Map.entry("0014005", "DISABLED"),
            Map.entry("0014006", "FARMER"),
            Map.entry("0014007", "MILITARY"),
            Map.entry("0014008", "LOCAL_TALENT")
    );

    private static final Map<String, String> SIDO_PREFIX_TO_ENUM = Map.ofEntries(
            Map.entry("11", "SEOUL"),
            Map.entry("26", "BUSAN"),
            Map.entry("27", "DAEGU"),
            Map.entry("28", "INCHEON"),
            Map.entry("29", "GWANGJU"),
            Map.entry("30", "DAEJEON"),
            Map.entry("31", "ULSAN"),
            Map.entry("36", "SEJONG"),
            Map.entry("41", "GYEONGGI"),
            Map.entry("51", "GANGWON"),
            Map.entry("42", "GANGWON"),     // legacy
            Map.entry("43", "CHUNGBUK"),
            Map.entry("44", "CHUNGNAM"),
            Map.entry("52", "JEONBUK"),
            Map.entry("45", "JEONBUK"),     // legacy
            Map.entry("46", "JEONNAM"),
            Map.entry("47", "GYEONGBUK"),
            Map.entry("48", "GYEONGNAM"),
            Map.entry("50", "JEJU")
    );

    private static final int TOTAL_SIDO_COUNT = 17;

    public List<EligibilityRule> extract(Long policyId, CodeBasedExtractionInput input) {
        List<EligibilityRule> rules = new ArrayList<>();
        rules.add(buildAgeRule(policyId, input));
        rules.add(buildMaritalStatusRule(policyId, input));
        rules.add(buildAnnualIncomeRule(policyId, input));
        rules.add(buildEmploymentKindRule(policyId, input));
        rules.add(buildEducationRule(policyId, input));
        rules.add(buildMajorFieldRule(policyId, input));
        rules.add(buildSpecializationRule(policyId, input));
        rules.add(buildRegionRule(policyId, input));
        return rules;
    }

    // ── age ──
    private EligibilityRule buildAgeRule(Long policyId, CodeBasedExtractionInput input) {
        if ("N".equals(input.ageLimitYn())) {
            return rule(policyId, "age", "연령", RuleOperator.ANY, "ALL",
                    "getPlcy.sprtTrgtAgeLmtYn: N");
        }
        Integer min = input.ageMin();
        Integer max = input.ageMax();
        boolean hasMin = min != null && min > 0;
        boolean hasMax = max != null && max > 0;
        if (hasMin && hasMax) {
            return rule(policyId, "age", "연령", RuleOperator.BETWEEN, min + "~" + max,
                    "getPlcy.sprtTrgtMinAge: " + min + ", sprtTrgtMaxAge: " + max);
        }
        if (hasMin) {
            return rule(policyId, "age", "연령", RuleOperator.GTE, String.valueOf(min),
                    "getPlcy.sprtTrgtMinAge: " + min);
        }
        if (hasMax) {
            return rule(policyId, "age", "연령", RuleOperator.LTE, String.valueOf(max),
                    "getPlcy.sprtTrgtMaxAge: " + max);
        }
        return rule(policyId, "age", "연령", RuleOperator.ANY, "ALL",
                "getPlcy.sprtTrgtMinAge·sprtTrgtMaxAge: 미지정");
    }

    // ── maritalStatus ──
    private EligibilityRule buildMaritalStatusRule(Long policyId, CodeBasedExtractionInput input) {
        String code = input.maritalStatusCd();
        String src = "getPlcy.mrgSttsCd: " + (code == null ? "미지정" : code);
        if ("0055001".equals(code)) {
            return rule(policyId, "maritalStatus", "결혼상태", RuleOperator.EQ, "MARRIED", src);
        }
        if ("0055002".equals(code)) {
            return rule(policyId, "maritalStatus", "결혼상태", RuleOperator.EQ, "SINGLE", src);
        }
        return rule(policyId, "maritalStatus", "결혼상태", RuleOperator.ANY, "ALL", src);
    }

    // ── annualIncome ──
    private EligibilityRule buildAnnualIncomeRule(Long policyId, CodeBasedExtractionInput input) {
        String code = input.earnConditionCd();
        if ("0043002".equals(code)) {
            Integer min = input.earnMin();
            Integer max = input.earnMax();
            boolean hasMin = min != null && min > 0;
            boolean hasMax = max != null && max > 0;
            String srcMinMax = "getPlcy.earnMinAmt: " + (min == null ? "0" : min)
                    + ", earnMaxAmt: " + (max == null ? "0" : max);
            if (min != null && max != null && hasMax) {
                return rule(policyId, "annualIncome", "연소득", RuleOperator.BETWEEN,
                        min + "~" + max, srcMinMax);
            }
            if (hasMax) {
                return rule(policyId, "annualIncome", "연소득", RuleOperator.LTE,
                        String.valueOf(max), srcMinMax);
            }
            if (hasMin) {
                return rule(policyId, "annualIncome", "연소득", RuleOperator.GTE,
                        String.valueOf(min), srcMinMax);
            }
            return rule(policyId, "annualIncome", "연소득", RuleOperator.ANY, "ALL",
                    "getPlcy.earnCndSeCd: 0043002 (수치 미지정)");
        }
        if ("0043003".equals(code)) {
            String etc = input.earnEtcCn();
            String trimmed = etc == null ? "" : etc.trim();
            String snippet = trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
            return rule(policyId, "annualIncome", "연소득", RuleOperator.ANY, "ALL",
                    "getPlcy.earnEtcCn: " + (snippet.isEmpty() ? "(자유서술 없음)" : snippet));
        }
        return rule(policyId, "annualIncome", "연소득", RuleOperator.ANY, "ALL",
                "getPlcy.earnCndSeCd: " + (code == null ? "미지정" : code));
    }

    // ── employmentKind ──
    private EligibilityRule buildEmploymentKindRule(Long policyId, CodeBasedExtractionInput input) {
        String code = input.employmentKindCd();
        String src = "getPlcy.jobCd: " + (code == null ? "미지정" : code);
        if (code == null || "0013009".equals(code) || "0013010".equals(code)) {
            return rule(policyId, "employmentKind", "취업상태", RuleOperator.ANY, "ALL", src);
        }
        String enumName = JOB_CODE_TO_ENUM.get(code);
        if (enumName == null) {
            return rule(policyId, "employmentKind", "취업상태", RuleOperator.ANY, "ALL", src + " (미인식)");
        }
        return rule(policyId, "employmentKind", "취업상태", RuleOperator.EQ, enumName, src);
    }

    // ── education ──
    private EligibilityRule buildEducationRule(Long policyId, CodeBasedExtractionInput input) {
        String code = input.educationCd();
        String src = "getPlcy.schoolCd: " + (code == null ? "미지정" : code);
        if (code == null || "0049009".equals(code) || "0049010".equals(code)) {
            return rule(policyId, "education", "학력", RuleOperator.ANY, "ALL", src);
        }
        String enumName = SCHOOL_CODE_TO_ENUM.get(code);
        if (enumName == null) {
            return rule(policyId, "education", "학력", RuleOperator.ANY, "ALL", src + " (미인식)");
        }
        return rule(policyId, "education", "학력", RuleOperator.EQ, enumName, src);
    }

    // ── majorField ──
    private EligibilityRule buildMajorFieldRule(Long policyId, CodeBasedExtractionInput input) {
        String code = input.majorFieldCd();
        String src = "getPlcy.plcyMajorCd: " + (code == null ? "미지정" : code);
        if (code == null || "0011008".equals(code) || "0011009".equals(code)) {
            return rule(policyId, "majorField", "전공", RuleOperator.ANY, "ALL", src);
        }
        String enumName = MAJOR_CODE_TO_ENUM.get(code);
        if (enumName == null) {
            return rule(policyId, "majorField", "전공", RuleOperator.ANY, "ALL", src + " (미인식)");
        }
        return rule(policyId, "majorField", "전공", RuleOperator.EQ, enumName, src);
    }

    // ── specializationField ──
    private EligibilityRule buildSpecializationRule(Long policyId, CodeBasedExtractionInput input) {
        String code = input.specializationCd();
        String src = "getPlcy.sbizCd: " + (code == null ? "미지정" : code);
        if (code == null || "0014009".equals(code) || "0014010".equals(code)) {
            return rule(policyId, "specializationField", "특화요건", RuleOperator.ANY, "ALL", src);
        }
        String enumName = SBIZ_CODE_TO_ENUM.get(code);
        if (enumName == null) {
            return rule(policyId, "specializationField", "특화요건", RuleOperator.ANY, "ALL", src + " (미인식)");
        }
        return rule(policyId, "specializationField", "특화요건", RuleOperator.EQ, enumName, src);
    }

    // ── region ──
    private EligibilityRule buildRegionRule(Long policyId, CodeBasedExtractionInput input) {
        List<String> codes = input.zipCodes();
        if (codes == null || codes.isEmpty()) {
            return rule(policyId, "region", "거주지", RuleOperator.ANY, "ALL",
                    "getPlcy.zipCd: 미지정");
        }
        LinkedHashSet<String> sidos = new LinkedHashSet<>();
        for (String c : codes) {
            if (c == null || c.length() < 2) continue;
            String prefix = c.substring(0, 2);
            String e = SIDO_PREFIX_TO_ENUM.get(prefix);
            if (e != null) sidos.add(e);
        }
        String src = "getPlcy.zipCd: " + codes.size() + "개 코드 → 시도 " + sidos.size() + "개";
        if (sidos.isEmpty()) {
            return rule(policyId, "region", "거주지", RuleOperator.ANY, "ALL", src);
        }
        if (sidos.size() == 1) {
            String only = sidos.iterator().next();
            return rule(policyId, "region", "거주지", RuleOperator.EQ, only, src);
        }
        if (sidos.size() >= TOTAL_SIDO_COUNT) {
            return rule(policyId, "region", "거주지", RuleOperator.ANY, "ALL", src + " (전국)");
        }
        return rule(policyId, "region", "거주지", RuleOperator.IN, String.join(",", sidos), src);
    }

    // ── 빌더 헬퍼 ──
    private EligibilityRule rule(Long policyId, String field, String label,
                                  RuleOperator operator, String value, String sourceRef) {
        return EligibilityRule.builder()
                .policyId(policyId)
                .field(field)
                .operator(operator)
                .value(value)
                .label(label)
                .sourceReference(sourceRef)
                .confidence(RuleConfidence.HIGH)
                .extractionVersion(EXTRACTION_VERSION)
                .build();
    }
}
