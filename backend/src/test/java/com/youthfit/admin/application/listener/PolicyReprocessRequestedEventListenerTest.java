package com.youthfit.admin.application.listener;

import com.youthfit.common.event.PolicyReprocessRequestedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.service.RagIndexingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("PolicyReprocessRequestedEventListener")
@ExtendWith(MockitoExtension.class)
class PolicyReprocessRequestedEventListenerTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyProcessingStepService stepService;
    @Mock private RagIndexingService ragIndexingService;
    @Mock private GuideGenerationService guideGenerationService;
    @Mock private EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @InjectMocks
    private PolicyReprocessRequestedEventListener listener;

    @Test
    @DisplayName("정책 존재 시 ENRICHMENT(SKIPPED) → GUIDE → RULE → RAG 순으로 호출하고 stepIds 를 순서대로 마감한다")
    void onPolicyReprocessRequested_executesAllFourStepsInOrder() {
        // given
        Policy policy = mock(Policy.class);
        given(policy.getId()).willReturn(100L);
        given(policy.getTitle()).willReturn("청년월세 지원");
        given(policy.getBody()).willReturn("본문");
        given(policy.getEnrichment()).willReturn(null);
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));

        List<Long> stepIds = List.of(11L, 12L, 13L, 14L);
        PolicyReprocessRequestedEvent event = new PolicyReprocessRequestedEvent(100L, "운영자 요청", stepIds);

        // when
        listener.onPolicyReprocessRequested(event);

        // then — InOrder 로 호출 순서 검증
        InOrder order = inOrder(stepService, guideGenerationService, eligibilityRuleGenerationService, ragIndexingService);
        order.verify(stepService).markFinished(eq(11L), eq(ProcessingStatus.SKIPPED), any(), any());
        order.verify(guideGenerationService).generateGuide(any(GenerateGuideCommand.class));
        order.verify(stepService).markFinished(eq(12L), eq(ProcessingStatus.SUCCESS), any(), any());
        order.verify(eligibilityRuleGenerationService).generateRules(any(GenerateEligibilityRulesCommand.class));
        order.verify(stepService).markFinished(eq(13L), eq(ProcessingStatus.SUCCESS), any(), any());
        order.verify(ragIndexingService).indexPolicyDocument(any(IndexPolicyDocumentCommand.class));
        order.verify(stepService).markFinished(eq(14L), eq(ProcessingStatus.SUCCESS), any(), any());
    }

    @Test
    @DisplayName("정책이 없으면 4개 stepIds 전부 FAILED 로 마감하고 service 호출 안 함")
    void onPolicyReprocessRequested_policyMissing_marksAllFailed() {
        // given
        given(policyRepository.findById(999L)).willReturn(Optional.empty());
        List<Long> stepIds = List.of(21L, 22L, 23L, 24L);
        PolicyReprocessRequestedEvent event = new PolicyReprocessRequestedEvent(999L, "사유", stepIds);

        // when
        listener.onPolicyReprocessRequested(event);

        // then
        verify(stepService).markFinished(eq(21L), eq(ProcessingStatus.FAILED), eq("정책 없음"), eq(null));
        verify(stepService).markFinished(eq(22L), eq(ProcessingStatus.FAILED), eq("정책 없음"), eq(null));
        verify(stepService).markFinished(eq(23L), eq(ProcessingStatus.FAILED), eq("정책 없음"), eq(null));
        verify(stepService).markFinished(eq(24L), eq(ProcessingStatus.FAILED), eq("정책 없음"), eq(null));
        verify(guideGenerationService, never()).generateGuide(any());
        verify(eligibilityRuleGenerationService, never()).generateRules(any());
        verify(ragIndexingService, never()).indexPolicyDocument(any());
    }

    @Test
    @DisplayName("GUIDE 단계 실패해도 RULE/RAG 계속 진행하고, 실패한 step 만 FAILED 로 마감한다")
    void onPolicyReprocessRequested_guideFailure_doesNotBlockSubsequentSteps() {
        // given
        Policy policy = mock(Policy.class);
        given(policy.getId()).willReturn(100L);
        given(policy.getTitle()).willReturn("청년월세");
        given(policy.getBody()).willReturn("본문");
        given(policy.getEnrichment()).willReturn(null);
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));
        given(guideGenerationService.generateGuide(any()))
                .willThrow(new RuntimeException("LLM rate limit"));

        List<Long> stepIds = List.of(31L, 32L, 33L, 34L);
        PolicyReprocessRequestedEvent event = new PolicyReprocessRequestedEvent(100L, "사유", stepIds);

        // when
        listener.onPolicyReprocessRequested(event);

        // then
        verify(stepService).markFinished(eq(31L), eq(ProcessingStatus.SKIPPED), any(), any()); // ENRICHMENT
        verify(stepService).markFinished(eq(32L), eq(ProcessingStatus.FAILED), eq("LLM rate limit"), eq(null)); // GUIDE
        verify(eligibilityRuleGenerationService).generateRules(any()); // RULE 계속 호출
        verify(stepService).markFinished(eq(33L), eq(ProcessingStatus.SUCCESS), any(), any()); // RULE
        verify(ragIndexingService).indexPolicyDocument(any()); // RAG 계속 호출
        verify(stepService).markFinished(eq(34L), eq(ProcessingStatus.SUCCESS), any(), any()); // RAG
    }

    @Test
    @DisplayName("ENRICHMENT 단계는 SKIPPED 로 마감되고 reason 은 retryStep 과 동일한 메시지")
    void onPolicyReprocessRequested_enrichmentMarkedSkippedWithRetryStepMessage() {
        // given
        Policy policy = mock(Policy.class);
        given(policy.getId()).willReturn(100L);
        given(policy.getTitle()).willReturn("t");
        given(policy.getBody()).willReturn("b");
        given(policy.getEnrichment()).willReturn(null);
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));

        List<Long> stepIds = List.of(41L, 42L, 43L, 44L);
        PolicyReprocessRequestedEvent event = new PolicyReprocessRequestedEvent(100L, "사유", stepIds);

        // when
        listener.onPolicyReprocessRequested(event);

        // then — reason 메시지가 retryStep(ENRICHMENT) 와 정확히 일치하는지 검증
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(stepService).markFinished(eq(41L), eq(ProcessingStatus.SKIPPED), reason.capture(), eq(null));
        assertThat(reason.getValue()).isEqualTo("MVP: ENRICHMENT manual trigger 미연결");
    }

    private static <T> T mock(Class<T> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }
}
