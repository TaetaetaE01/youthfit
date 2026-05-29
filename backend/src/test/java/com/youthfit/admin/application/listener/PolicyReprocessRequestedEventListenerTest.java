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

    private static <T> T mock(Class<T> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }
}
