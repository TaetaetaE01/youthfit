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
import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.UserRepository;
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
    private UserRepository userRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Nested
    @DisplayName("judgeEligibility - 적합도 판정")
    class JudgeEligibility {

        @Test
        @DisplayName("모든 기준이 충족되면 LIKELY_ELIGIBLE을 반환한다")
        void allCriteriaMet_returnsLikelyEligible() {
            // given
            User user = createMockUser(29, "11", 30000000L);
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령"),
                    createRule("region", RuleOperator.EQ, "11", "거주지")
            );
            setupMocks(user, policy, rules);

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
            User user = createMockUser(36, "11", 30000000L);
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령"),
                    createRule("region", RuleOperator.EQ, "11", "거주지")
            );
            setupMocks(user, policy, rules);

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.overallResult()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        }

        @Test
        @DisplayName("필드값이 누락되면 UNCERTAIN을 반환하고 missingFields에 포함한다")
        void missingField_returnsUncertain() {
            // given
            User user = createMockUser(29, "11", null);
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령"),
                    createRule("annualIncome", RuleOperator.LTE, "50000000", "소득 상한")
            );
            setupMocks(user, policy, rules);

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
            User user = createMockUser(36, "11", null);
            Policy policy = createMockPolicy();
            List<EligibilityRule> rules = List.of(
                    createRule("age", RuleOperator.BETWEEN, "19~34", "연령"),
                    createRule("annualIncome", RuleOperator.LTE, "50000000", "소득 상한")
            );
            setupMocks(user, policy, rules);

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.overallResult()).isEqualTo(EligibilityResult.LIKELY_INELIGIBLE);
        }

        @Test
        @DisplayName("규칙이 없으면 LIKELY_ELIGIBLE을 반환한다")
        void noRules_returnsLikelyEligible() {
            // given
            User user = createMockUser(29, "11", 30000000L);
            Policy policy = createMockPolicy();
            setupMocks(user, policy, List.of());

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
            User user = createMockUser(29, "11", 30000000L);
            Policy policy = createMockPolicy();
            setupMocks(user, policy, List.of());

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
            User user = createMockUser(29, "11", 30000000L);
            Policy policy = createMockPolicy();
            setupMocks(user, policy, List.of());

            // when
            EligibilityJudgmentResult result = eligibilityService.judgeEligibility(1L, new JudgeEligibilityCommand(1L));

            // then
            assertThat(result.policyId()).isEqualTo(1L);
            assertThat(result.policyTitle()).isEqualTo("2026 청년월세지원");
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionCases {

        @Test
        @DisplayName("존재하지 않는 사용자이면 NOT_FOUND 예외가 발생한다")
        void userNotFound_throwsException() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> eligibilityService.judgeEligibility(999L, new JudgeEligibilityCommand(1L)))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("존재하지 않는 정책이면 NOT_FOUND 예외가 발생한다")
        void policyNotFound_throwsException() {
            // given
            User user = createMockUser(29, "11", 30000000L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
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

    private void setupMocks(User user, Policy policy, List<EligibilityRule> rules) {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
        given(eligibilityRuleRepository.findAllByPolicyId(1L)).willReturn(rules);
    }

    private User createMockUser(Integer age, String region, Long annualIncome) {
        User user = User.builder()
                .email("test@test.com")
                .nickname("테스터")
                .authProvider(AuthProvider.KAKAO)
                .providerId("12345")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "age", age);
        ReflectionTestUtils.setField(user, "region", region);
        ReflectionTestUtils.setField(user, "annualIncome", annualIncome);
        return user;
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
