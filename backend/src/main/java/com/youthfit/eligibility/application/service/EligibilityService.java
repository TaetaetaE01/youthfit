package com.youthfit.eligibility.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.CriterionResult;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.dto.result.GroupedCriteria;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.view.SourceView;
import com.youthfit.eligibility.domain.model.view.SummaryView;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.UserValueView;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;
import com.youthfit.eligibility.domain.service.EligibilityEvaluator;
import com.youthfit.eligibility.domain.service.RequirementFormatter;
import com.youthfit.eligibility.domain.service.UserValueFormatter;
import com.youthfit.eligibility.domain.service.VerdictTextGenerator;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EligibilityService {

    private final EligibilityRuleRepository eligibilityRuleRepository;
    private final EligibilityProfileRepository eligibilityProfileRepository;
    private final PolicyRepository policyRepository;

    private final EligibilityEvaluator evaluator = new EligibilityEvaluator();
    private final RequirementFormatter requirementFormatter = new RequirementFormatter();
    private final UserValueFormatter userValueFormatter = new UserValueFormatter();
    private final VerdictTextGenerator verdictGenerator = new VerdictTextGenerator();
    private final SummaryHeadlineGenerator summaryGenerator = new SummaryHeadlineGenerator();

    @Transactional(readOnly = true)
    public EligibilityJudgmentResult judgeEligibility(Long userId, JudgeEligibilityCommand command) {
        EligibilityProfile profile = eligibilityProfileRepository.findByUserId(userId)
                .orElseGet(() -> EligibilityProfile.empty(userId));

        Policy policy = policyRepository.findById(command.policyId())
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다"));

        List<EligibilityRule> rules = eligibilityRuleRepository.findAllByPolicyId(command.policyId());

        List<CriterionEvaluation> evaluations = rules.stream()
                .map(rule -> evaluator.evaluateRule(rule, profile))
                .toList();

        List<CriterionResult> results = evaluations.stream()
                .map(this::toCriterionResult)
                .toList();

        GroupedCriteria grouped = groupByResult(results);
        SummaryView summary = summaryGenerator.generate(evaluations);
        EligibilityResult overall = determineOverall(evaluations);

        return new EligibilityJudgmentResult(
                policy.getId(),
                policy.getTitle(),
                overall.name(),
                summary,
                grouped,
                EligibilityJudgmentResult.DISCLAIMER_TEXT
        );
    }

    private CriterionResult toCriterionResult(CriterionEvaluation eval) {
        EligibilityRule rule = eval.rule();
        RequirementView requirement = requirementFormatter.format(
                rule.getField(), rule.getOperator(), rule.getValue()
        );
        UserValueView userValue = userValueFormatter.format(rule.getField(), eval.userValue());
        String verdictText = verdictGenerator.generate(
                eval.result(), eval.uncertainReason(), rule.getLabel(), requirement, userValue
        );
        SourceView source = new SourceView(rule.getSourceReference());
        return new CriterionResult(
                rule.getField(),
                rule.getLabel(),
                eval.result().name(),
                eval.uncertainReason(),
                requirement,
                userValue,
                verdictText,
                source
        );
    }

    private GroupedCriteria groupByResult(List<CriterionResult> results) {
        return new GroupedCriteria(
                results.stream().filter(r -> "LIKELY_INELIGIBLE".equals(r.result())).toList(),
                results.stream().filter(r -> "UNCERTAIN".equals(r.result())).toList(),
                results.stream().filter(r -> "LIKELY_ELIGIBLE".equals(r.result())).toList()
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
