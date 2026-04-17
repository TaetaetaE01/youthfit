package com.youthfit.eligibility.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@DisplayName("EligibilityService")
@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {

    @InjectMocks
    private EligibilityService eligibilityService;

    @Mock
    private EligibilityRuleRepository eligibilityRuleRepository;

    @Mock
    private EligibilityProfileRepository eligibilityProfileRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Nested
    @DisplayName("judgeEligibility - 적합도 판정")
    class JudgeEligibility {

        @Test
        @DisplayName("모든 기준이 충족되면 LIKELY_ELIGIBLE을 반환한다")
        void allCriteriaMet_returnsLikelyEligible() {
            // given
            EligibilityProfile profile = createMockProfile(29, "1100000000", 30000000L);
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령"),
                    createRule("region", RuleOperator.EQ, "1100000000", "거주지")
            );
            setupMocks(profile, policy, rules);

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.overallResult()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
            assertThat(result.criteria()).hasSize(2);
            assertThat(result.missingFields()).isEmpty();
        }

        @Test
        @DisplayName("하나의 기준이 미충족이면 LIKELY_INELIGIBLE을 반환한다")
        void oneCriterionFails_returnsLikelyIneligible() {
            // given
            EligibilityProfile profile = createMockProfile(36, "1100000000", 30000000L);
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령"),
                    createRule("region", RuleOperator.EQ, "1100000000", "거주지")
            );
            setupMocks(profile, policy, rules);

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.overallResult()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        }

        @Test
        @DisplayName("필드값이 누락되면 UNCERTAIN을 반환하고 missingFields에 포함한다")
        void missingField_returnsUncertain() {
            // given
            EligibilityProfile profile = createMockProfile(29, "1100000000", null);
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령"),
                    createRule("annualIncome", RuleOperator.LTE, "50000000", "소득 상한")
            );
            setupMocks(profile, policy, rules);

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.overallResult()).isEqualTo(EligibilityResult.UNCERTAIN);
            assertThat(result.missingFields()).containsExactly("annualIncome");
        }

        @Test
        @DisplayName("INELIGIBLE이 UNCERTAIN보다 우선한다")
        void ineligibleTakesPriorityOverUncertain() {
            // given
            EligibilityProfile profile = createMockProfile(36, "1100000000", null);
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령"),
                    createRule("annualIncome", RuleOperator.LTE, "50000000", "소득 상한")
            );
            setupMocks(profile, policy, rules);

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.overallResult()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        }

        @Test
        @DisplayName("규칙이 없으면 LIKELY_ELIGIBLE을 반환한다")
        void noRules_returnsLikelyEligible() {
            // given
            EligibilityProfile profile = createMockProfile(29, "1100000000", 30000000L);
            Policy policy = createMockPolicy();
            setupMocks(profile, policy, List.of());

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.overallResult()).isEqualTo(EligibilityResult.LIKELY_ELIGIBLE);
            assertThat(result.criteria()).isEmpty();
        }

        @Test
        @DisplayName("disclaimer가 항상 포함된다")
        void disclaimerAlwaysPresent() {
            // given
            EligibilityProfile profile = createMockProfile(29, "1100000000", 30000000L);
            Policy policy = createMockPolicy();
            setupMocks(profile, policy, List.of());

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.disclaimer()).isNotNull();
            assertThat(result.disclaimer()).contains("참고용");
        }

        @Test
        @DisplayName("정책 정보가 응답에 포함된다")
        void policyInfoIncluded() {
            // given
            EligibilityProfile profile = createMockProfile(29, "1100000000", 30000000L);
            Policy policy = createMockPolicy();
            setupMocks(profile, policy, List.of());

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.policyId()).isEqualTo(1L);
            assertThat(result.policyTitle()).isEqualTo("2026 청년월세지원");
        }

        @Test
        @DisplayName("적합도 프로필이 없으면 빈 프로필로 판정한다")
        void missingProfile_usesEmptyProfile() {
            // given
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령")
            );
            given(eligibilityProfileRepository.findByUserId(1L)).willReturn(Optional.empty());
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(eligibilityRuleRepository.findAllByPolicyId(1L)).willReturn(rules);

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.overallResult()).isEqualTo(EligibilityResult.UNCERTAIN);
            assertThat(result.missingFields()).containsExactly("age");
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionCases {

        @Test
        @DisplayName("존재하지 않는 정책이면 NOT_FOUND 예외가 발생한다")
        void policyNotFound_throwsException() {
            // given
            EligibilityProfile profile = createMockProfile(29, "1100000000", 30000000L);
            given(eligibilityProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));
            given(policyRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(999L)))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    });
        }
    }

    // ── 헬퍼 메서드 ──

    private void setupMocks(EligibilityProfile profile, Policy policy, List<EligibilityRule> rules) {
        given(eligibilityProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));
        given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
        given(eligibilityRuleRepository.findAllByPolicyId(1L)).willReturn(rules);
    }

    private EligibilityProfile createMockProfile(Integer age, String legalDongCode, Long incomeMax) {
        EligibilityProfile profile = EligibilityProfile.empty(1L);
        ReflectionTestUtils.setField(profile, "id", 1L);
        if (age != null) {
            profile.changeAge(age);
        }
        if (legalDongCode != null) {
            profile.changeLegalDongCode(legalDongCode);
        }
        if (incomeMax != null) {
            profile.changeIncomeRange(null, incomeMax);
        }
        return profile;
    }

    private Policy createMockPolicy() {
        Policy policy = Policy.builder()
                .title("2026 청년월세지원")
                .summary("월세 지원 정책")
                .category(Category.HOUSING)
                .regionCode("11")
                .applyStart(LocalDate.of(2026, 4, 1))
                .applyEnd(LocalDate.of(2026, 5, 31))
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        return policy;
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
