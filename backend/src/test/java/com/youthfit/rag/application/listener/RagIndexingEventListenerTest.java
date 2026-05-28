package com.youthfit.rag.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("RagIndexingEventListener")
@ExtendWith(MockitoExtension.class)
class RagIndexingEventListenerTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private RagIndexingService ragIndexingService;

    @InjectMocks
    private RagIndexingEventListener listener;

    @Test
    @DisplayName("PolicyUpsertedEvent 수신 시 policyId/body/enrichment 로 indexPolicyDocument 를 호출한다")
    void onPolicyUpserted_callsIndexing() {
        Long policyId = 42L;
        PolicyEnrichment enrichment = mock(PolicyEnrichment.class);
        Policy policy = mock(Policy.class);
        given(policy.getBody()).willReturn("정책 본문입니다.");
        given(policy.getEnrichment()).willReturn(enrichment);
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy));
        given(ragIndexingService.indexPolicyDocument(any()))
                .willReturn(new IndexingResult(policyId, 3, true));

        listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "청년월세 지원"));

        ArgumentCaptor<IndexPolicyDocumentCommand> captor =
                ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
        then(ragIndexingService).should().indexPolicyDocument(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(policyId);
        assertThat(captor.getValue().content()).isEqualTo("정책 본문입니다.");
        assertThat(captor.getValue().enrichment()).isSameAs(enrichment);
    }

    @Test
    @DisplayName("정책의 body 가 null 이면 indexPolicyDocument 를 호출하지 않는다")
    void onPolicyUpserted_skipsWhenBodyIsNull() {
        Long policyId = 11L;
        Policy policy = mock(Policy.class);
        given(policy.getBody()).willReturn(null);
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy));

        listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "t"));

        then(ragIndexingService).should(never()).indexPolicyDocument(any());
    }

    @Test
    @DisplayName("정책의 body 가 공백만 있으면 indexPolicyDocument 를 호출하지 않는다")
    void onPolicyUpserted_skipsWhenBodyIsBlank() {
        Long policyId = 12L;
        Policy policy = mock(Policy.class);
        given(policy.getBody()).willReturn("   \n\t  ");
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy));

        listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "t"));

        then(ragIndexingService).should(never()).indexPolicyDocument(any());
    }

    @Test
    @DisplayName("정책이 존재하지 않으면 indexPolicyDocument 를 호출하지 않는다")
    void onPolicyUpserted_skipsWhenPolicyNotFound() {
        Long policyId = 99L;
        given(policyRepository.findById(policyId)).willReturn(Optional.empty());

        listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "t"));

        then(ragIndexingService).should(never()).indexPolicyDocument(any());
    }

    @Test
    @DisplayName("RAG 인덱싱에서 예외가 던져져도 리스너는 예외를 전파하지 않는다")
    void onPolicyUpserted_swallowsException() {
        Long policyId = 1L;
        Policy policy = mock(Policy.class);
        given(policy.getBody()).willReturn("본문");
        given(policy.getEnrichment()).willReturn(null);
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy));
        given(ragIndexingService.indexPolicyDocument(any()))
                .willThrow(new RuntimeException("OpenAI 임베딩 장애"));

        assertThatCode(() -> listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "t")))
                .doesNotThrowAnyException();
    }
}
