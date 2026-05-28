package com.youthfit.guide.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DisplayName("GuideGenerationEventListener")
@ExtendWith(MockitoExtension.class)
class GuideGenerationEventListenerTest {

    @Mock
    private GuideGenerationService guideGenerationService;

    @Mock
    private PolicyProcessingStepService stepService;

    @InjectMocks
    private GuideGenerationEventListener listener;

    @Test
    @DisplayName("PolicyUpsertedEvent 수신 시 policyId/title 로 가이드 생성을 호출한다")
    void onPolicyUpserted_callsGuideGeneration() {
        // given
        PolicyUpsertedEvent event = new PolicyUpsertedEvent(42L, "청년월세 지원");

        // when
        listener.onPolicyUpserted(event);

        // then
        ArgumentCaptor<GenerateGuideCommand> captor = ArgumentCaptor.forClass(GenerateGuideCommand.class);
        then(guideGenerationService).should().generateGuide(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(42L);
        assertThat(captor.getValue().policyTitle()).isEqualTo("청년월세 지원");
    }

    @Test
    @DisplayName("PolicyAttachmentReindexedEvent 수신 시 policyId 만으로 가이드 재생성을 호출한다 (title null)")
    void onAttachmentReindexed_callsGuideGenerationWithoutTitle() {
        // given
        PolicyAttachmentReindexedEvent event = new PolicyAttachmentReindexedEvent(7L);

        // when
        listener.onAttachmentReindexed(event);

        // then
        ArgumentCaptor<GenerateGuideCommand> captor = ArgumentCaptor.forClass(GenerateGuideCommand.class);
        then(guideGenerationService).should().generateGuide(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(7L);
        assertThat(captor.getValue().policyTitle()).isNull();
    }

    @Test
    @DisplayName("가이드 생성에서 예외가 던져져도 리스너는 예외를 전파하지 않는다 (PolicyUpsertedEvent)")
    void onPolicyUpserted_swallowsException() {
        // given
        given(guideGenerationService.generateGuide(any()))
                .willThrow(new RuntimeException("LLM 장애"));

        // when & then
        assertThatCode(() -> listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "t")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("가이드 재생성에서 예외가 던져져도 리스너는 예외를 전파하지 않는다 (PolicyAttachmentReindexedEvent)")
    void onAttachmentReindexed_swallowsException() {
        // given
        given(guideGenerationService.generateGuide(any()))
                .willThrow(new RuntimeException("LLM 장애"));

        // when & then
        assertThatCode(() -> listener.onAttachmentReindexed(new PolicyAttachmentReindexedEvent(1L)))
                .doesNotThrowAnyException();
    }
}
