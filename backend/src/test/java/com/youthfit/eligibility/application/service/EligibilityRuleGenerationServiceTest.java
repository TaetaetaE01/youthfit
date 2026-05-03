package com.youthfit.eligibility.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.application.dto.result.RuleGenerationResult;
import com.youthfit.eligibility.application.port.EligibilityRuleLlmProvider;
import com.youthfit.eligibility.application.service.EligibilityRuleValidator.ValidationReport;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class EligibilityRuleGenerationServiceTest {

    private CostGuard costGuard;
    private PolicyRepository policyRepository;
    private PolicyDocumentRepository policyDocumentRepository;
    private EligibilityRuleRepository ruleRepository;
    private EligibilityRuleLlmProvider llmProvider;
    private EligibilityRuleValidator validator;

    private EligibilityRuleGenerationService service;

    @BeforeEach
    void setUp() {
        costGuard = mock(CostGuard.class);
        policyRepository = mock(PolicyRepository.class);
        policyDocumentRepository = mock(PolicyDocumentRepository.class);
        ruleRepository = mock(EligibilityRuleRepository.class);
        llmProvider = mock(EligibilityRuleLlmProvider.class);
        validator = mock(EligibilityRuleValidator.class);

        service = new EligibilityRuleGenerationService(
                costGuard, policyRepository, policyDocumentRepository,
                ruleRepository, llmProvider, validator);

        when(costGuard.allows(anyLong())).thenReturn(true);
    }

    @Test
    void costGuardSkip_doesNotCallLlm() {
        when(costGuard.allows(1L)).thenReturn(false);

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isFalse();
        verifyNoInteractions(llmProvider);
        verify(costGuard).logSkip(eq("generateRules"), eq(1L));
    }

    @Test
    void policyNotFound_returnsFailure() {
        when(policyRepository.findById(1L)).thenReturn(Optional.empty());

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isFalse();
        verifyNoInteractions(llmProvider);
    }

    @Test
    void normalFlow_callsDeleteAndSaveAll() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());

        RawExtractedRule raw = new RawExtractedRule(
                "age", "BETWEEN", "19~34", "연령", "자격 > 연령", "HIGH");
        when(llmProvider.extractRules(any())).thenReturn(List.of(raw));
        when(validator.validate(List.of(raw)))
                .thenReturn(new ValidationReport(List.of(raw), List.of(), false));

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isTrue();
        verify(ruleRepository).deleteAllByPolicyId(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EligibilityRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getField()).isEqualTo("age");
        assertThat(captor.getValue().get(0).getSourceHash()).isNotBlank();
        assertThat(captor.getValue().get(0).getExtractionVersion())
                .isEqualTo(EligibilityRuleGenerationService.PROMPT_VERSION);
    }

    @Test
    void retryTriggered_callsRegenerateOnce() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());

        RawExtractedRule bad = new RawExtractedRule("householdSize", "EQ", "1", "x", "y", "HIGH");
        RawExtractedRule good = new RawExtractedRule("age", "BETWEEN", "19~34", "연령", "자격", "HIGH");

        when(llmProvider.extractRules(any())).thenReturn(List.of(bad));
        when(validator.validate(List.of(bad)))
                .thenReturn(new ValidationReport(List.of(), List.of("폐기: householdSize"), true));

        when(llmProvider.regenerateWithFeedback(any(), anyList())).thenReturn(List.of(good));
        when(validator.validate(List.of(good)))
                .thenReturn(new ValidationReport(List.of(good), List.of(), false));

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isTrue();
        verify(llmProvider).regenerateWithFeedback(any(), anyList());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EligibilityRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getField()).isEqualTo("age");
    }

    @Test
    void llmFailure_keepsExistingRules() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());
        when(llmProvider.extractRules(any())).thenThrow(new IllegalStateException("OpenAI 502"));

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isFalse();
        verify(ruleRepository, never()).deleteAllByPolicyId(anyLong());
        verify(ruleRepository, never()).saveAll(anyList());
    }

    @Test
    void emptyExtraction_stillDeletesAndSavesEmpty() {
        Policy policy = mockPolicy(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());
        when(llmProvider.extractRules(any())).thenReturn(List.of());
        when(validator.validate(List.of()))
                .thenReturn(new ValidationReport(List.of(), List.of(), false));

        RuleGenerationResult result = service.generateRules(new GenerateEligibilityRulesCommand(1L));

        assertThat(result.generated()).isTrue();
        verify(ruleRepository).deleteAllByPolicyId(1L);
        verify(ruleRepository).saveAll(List.of());
    }

    private Policy mockPolicy(Long id) {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(id);
        when(policy.getTitle()).thenReturn("테스트 정책");
        when(policy.getSummary()).thenReturn("요약");
        when(policy.getSupportTarget()).thenReturn("만 19~34세");
        when(policy.getSelectionCriteria()).thenReturn("서울 거주");
        when(policy.getSupportContent()).thenReturn("월세 지원");
        when(policy.getBody()).thenReturn("본문");
        return policy;
    }
}
