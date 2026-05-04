package com.youthfit.eligibility.application.listener;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
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

@DisplayName("EligibilityRuleGenerationEventListener")
@ExtendWith(MockitoExtension.class)
class EligibilityRuleGenerationEventListenerTest {

    @Mock
    private EligibilityRuleGenerationService eligibilityRuleGenerationService;

    @InjectMocks
    private EligibilityRuleGenerationEventListener listener;

    @Test
    @DisplayName("PolicyUpsertedEvent 수신 시 policyId 로 룰 추출을 호출한다")
    void onPolicyUpserted_callsRuleGeneration() {
        // given
        PolicyUpsertedEvent event = new PolicyUpsertedEvent(42L, "청년월세 지원");

        // when
        listener.onPolicyUpserted(event);

        // then
        ArgumentCaptor<GenerateEligibilityRulesCommand> captor =
                ArgumentCaptor.forClass(GenerateEligibilityRulesCommand.class);
        then(eligibilityRuleGenerationService).should().generateRules(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("PolicyAttachmentReindexedEvent 수신 시 policyId 로 룰 재추출을 호출한다")
    void onAttachmentReindexed_callsRuleGeneration() {
        // given
        PolicyAttachmentReindexedEvent event = new PolicyAttachmentReindexedEvent(7L);

        // when
        listener.onAttachmentReindexed(event);

        // then
        ArgumentCaptor<GenerateEligibilityRulesCommand> captor =
                ArgumentCaptor.forClass(GenerateEligibilityRulesCommand.class);
        then(eligibilityRuleGenerationService).should().generateRules(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("룰 추출에서 예외가 던져져도 리스너는 예외를 전파하지 않는다 (PolicyUpsertedEvent)")
    void onPolicyUpserted_swallowsException() {
        // given
        given(eligibilityRuleGenerationService.generateRules(any()))
                .willThrow(new RuntimeException("LLM 장애"));

        // when & then
        assertThatCode(() -> listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "t")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("룰 재추출에서 예외가 던져져도 리스너는 예외를 전파하지 않는다 (PolicyAttachmentReindexedEvent)")
    void onAttachmentReindexed_swallowsException() {
        // given
        given(eligibilityRuleGenerationService.generateRules(any()))
                .willThrow(new RuntimeException("LLM 장애"));

        // when & then
        assertThatCode(() -> listener.onAttachmentReindexed(new PolicyAttachmentReindexedEvent(1L)))
                .doesNotThrowAnyException();
    }
}
