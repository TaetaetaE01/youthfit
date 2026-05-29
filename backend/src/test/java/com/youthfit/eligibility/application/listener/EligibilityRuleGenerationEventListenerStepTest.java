package com.youthfit.eligibility.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EligibilityRuleGenerationEventListener - RULE step 기록")
class EligibilityRuleGenerationEventListenerStepTest {

    private EligibilityRuleGenerationService ruleService;
    private EligibilityRuleRepository ruleRepository;
    private PolicyProcessingStepService stepService;
    private EligibilityRuleGenerationEventListener listener;

    @BeforeEach
    void setUp() {
        ruleService = mock(EligibilityRuleGenerationService.class);
        ruleRepository = mock(EligibilityRuleRepository.class);
        stepService = mock(PolicyProcessingStepService.class);
        listener = new EligibilityRuleGenerationEventListener(ruleService, ruleRepository, stepService);
    }

    @Test
    @DisplayName("onPolicyUpserted: 성공 시 RULE SUCCESS 기록")
    void onPolicyUpserted_성공_시_RULE_SUCCESS_기록() {
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());
        when(stepService.markStarted(1L, ProcessingStep.RULE)).thenReturn(100L);

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트"));

        verify(stepService).markStarted(1L, ProcessingStep.RULE);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    @DisplayName("onPolicyUpserted: 예외 발생 시 RULE FAILED 기록 후 swallow")
    void onPolicyUpserted_예외_시_RULE_FAILED_기록_후_swallow() {
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of());
        when(stepService.markStarted(1L, ProcessingStep.RULE)).thenReturn(100L);
        doThrow(new RuntimeException("rule 추출 실패")).when(ruleService).generateRules(any());

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트"));

        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.FAILED), contains("rule 추출 실패"), isNull());
    }

    @Test
    @DisplayName("onPolicyUpserted: code-v1 룰 존재 시 RULE SKIPPED 기록 후 LLM 추출 스킵")
    void onPolicyUpserted_codeV1_룰_존재_시_RULE_SKIPPED_기록() {
        EligibilityRule codeRule = mock(EligibilityRule.class);
        when(codeRule.getExtractionVersion()).thenReturn("code-v1");
        when(ruleRepository.findAllByPolicyId(1L)).thenReturn(List.of(codeRule));
        when(stepService.markStarted(1L, ProcessingStep.RULE)).thenReturn(100L);

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트"));

        verify(ruleService, never()).generateRules(any());
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SKIPPED), contains("code-v1"), isNull());
    }

    @Test
    @DisplayName("onAttachmentReindexed: 성공 시 RULE SUCCESS 기록")
    void onAttachmentReindexed_성공_시_RULE_SUCCESS_기록() {
        when(ruleRepository.findAllByPolicyId(2L)).thenReturn(List.of());
        when(stepService.markStarted(2L, ProcessingStep.RULE)).thenReturn(200L);

        listener.onAttachmentReindexed(new PolicyAttachmentReindexedEvent(2L));

        verify(stepService).markStarted(2L, ProcessingStep.RULE);
        verify(stepService).markFinished(eq(200L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    @DisplayName("onAttachmentReindexed: 예외 발생 시 RULE FAILED 기록 후 swallow")
    void onAttachmentReindexed_예외_시_RULE_FAILED_기록_후_swallow() {
        when(ruleRepository.findAllByPolicyId(2L)).thenReturn(List.of());
        when(stepService.markStarted(2L, ProcessingStep.RULE)).thenReturn(200L);
        doThrow(new RuntimeException("재추출 실패")).when(ruleService).generateRules(any());

        listener.onAttachmentReindexed(new PolicyAttachmentReindexedEvent(2L));

        verify(stepService).markFinished(eq(200L), eq(ProcessingStatus.FAILED), contains("재추출 실패"), isNull());
    }
}
