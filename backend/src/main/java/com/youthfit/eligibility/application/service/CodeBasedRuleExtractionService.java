package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.eligibility.domain.service.CodeBasedRuleExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CodeBasedRuleExtractionService {

    private final CodeBasedRuleExtractor extractor;
    private final EligibilityRuleRepository ruleRepository;

    public void extractAndPersist(Long policyId, CodeBasedExtractionInput input) {
        List<EligibilityRule> rules = extractor.extract(policyId, input);
        ruleRepository.deleteAllByPolicyId(policyId);
        ruleRepository.saveAll(rules);
    }
}
