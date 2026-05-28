package com.youthfit.guide.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuideGenerationEventListener - GUIDE step 기록")
class GuideGenerationEventListenerStepTest {

    private GuideGenerationService guideService;
    private PolicyProcessingStepService stepService;
    private GuideGenerationEventListener listener;

    @BeforeEach
    void setUp() {
        guideService = mock(GuideGenerationService.class);
        stepService = mock(PolicyProcessingStepService.class);
        listener = new GuideGenerationEventListener(guideService, stepService);
    }

    @Test
    @DisplayName("PolicyUpsertedEvent 성공 시 GUIDE SUCCESS 기록")
    void onPolicyUpserted_성공_시_GUIDE_SUCCESS_기록() {
        when(stepService.markStarted(1L, ProcessingStep.GUIDE)).thenReturn(100L);

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트"));

        verify(stepService).markStarted(1L, ProcessingStep.GUIDE);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    @DisplayName("PolicyUpsertedEvent 예외 시 GUIDE FAILED 기록 후 swallow")
    void onPolicyUpserted_예외_시_GUIDE_FAILED_기록_후_swallow() {
        when(stepService.markStarted(1L, ProcessingStep.GUIDE)).thenReturn(100L);
        doThrow(new RuntimeException("LLM 실패")).when(guideService).generateGuide(any());

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.FAILED), reasonCaptor.capture(), isNull());
        Assertions.assertThat(reasonCaptor.getValue()).contains("LLM 실패");
    }

    @Test
    @DisplayName("PolicyAttachmentReindexedEvent 성공 시 GUIDE SUCCESS 기록")
    void onAttachmentReindexed_성공_시_GUIDE_SUCCESS_기록() {
        when(stepService.markStarted(7L, ProcessingStep.GUIDE)).thenReturn(200L);

        listener.onAttachmentReindexed(new PolicyAttachmentReindexedEvent(7L));

        verify(stepService).markStarted(7L, ProcessingStep.GUIDE);
        verify(stepService).markFinished(eq(200L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    @DisplayName("PolicyAttachmentReindexedEvent 예외 시 GUIDE FAILED 기록 후 swallow")
    void onAttachmentReindexed_예외_시_GUIDE_FAILED_기록_후_swallow() {
        when(stepService.markStarted(7L, ProcessingStep.GUIDE)).thenReturn(200L);
        doThrow(new RuntimeException("LLM 재생성 실패")).when(guideService).generateGuide(any());

        listener.onAttachmentReindexed(new PolicyAttachmentReindexedEvent(7L));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(stepService).markFinished(eq(200L), eq(ProcessingStatus.FAILED), reasonCaptor.capture(), isNull());
        Assertions.assertThat(reasonCaptor.getValue()).contains("LLM 재생성 실패");
    }
}
