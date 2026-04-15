package com.youthfit.eligibility.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.CriterionResult;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;
import com.youthfit.eligibility.domain.service.EligibilityEvaluator;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EligibilityService {

    private final EligibilityRuleRepository eligibilityRuleRepository;
    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;
    private final EligibilityEvaluator evaluator = new EligibilityEvaluator();

    @Transactional(readOnly = true)
    public EligibilityJudgmentResult judgeEligibility(Long userId, JudgeEligibilityCommand command) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        Policy policy = policyRepository.findById(command.policyId())
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다"));

        List<EligibilityRule> rules = eligibilityRuleRepository.findAllByPolicyId(command.policyId());

        List<CriterionEvaluation> evaluations = rules.stream()
                .map(rule -> evaluator.evaluateRule(rule, user))
                .toList();

        List<CriterionResult> criteria = evaluations.stream()
                .map(CriterionResult::from)
                .toList();

        List<String> missingFields = evaluations.stream()
                .filter(e -> e.result() == EligibilityResult.UNCERTAIN)
                .map(CriterionEvaluation::field)
                .toList();

        EligibilityResult overallResult = determineOverall(evaluations);

        return new EligibilityJudgmentResult(
                policy.getId(),
                policy.getTitle(),
                overallResult,
                criteria,
                missingFields,
                EligibilityJudgmentResult.DISCLAIMER_TEXT
        );
    }

    private EligibilityResult determineOverall(List<CriterionEvaluation> evaluations) {
        if (evaluations.stream().anyMatch(e -> e.result() == EligibilityResult.LIKELY_INELIGIBLE)) {
            return EligibilityResult.LIKELY_INELIGIBLE;
        }
        if (evaluations.stream().anyMatch(e -> e.result() == EligibilityResult.UNCERTAIN)) {
            return EligibilityResult.UNCERTAIN;
        }
        return EligibilityResult.LIKELY_ELIGIBLE;
    }
}
