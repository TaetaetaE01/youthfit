package com.youthfit.rag.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RagIndexingEventListener - RAG_INDEXING step 기록")
class RagIndexingEventListenerStepTest {

    private PolicyRepository policyRepository;
    private RagIndexingService ragIndexingService;
    private PolicyProcessingStepService stepService;
    private RagIndexingEventListener listener;

    @BeforeEach
    void setUp() {
        policyRepository = mock(PolicyRepository.class);
        ragIndexingService = mock(RagIndexingService.class);
        stepService = mock(PolicyProcessingStepService.class);
        listener = new RagIndexingEventListener(policyRepository, ragIndexingService, stepService);
    }

    @Test
    @DisplayName("PolicyUpsertedEvent 성공 시 RAG_INDEXING SUCCESS 기록")
    void onPolicyUpserted_성공_시_RAG_INDEXING_SUCCESS_기록() {
        Policy policy = mock(Policy.class);
        when(policy.getBody()).thenReturn("정책 본문");
        when(policy.getEnrichment()).thenReturn(null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(stepService.markStarted(1L, ProcessingStep.RAG_INDEXING)).thenReturn(100L);
        when(ragIndexingService.indexPolicyDocument(any())).thenReturn(new IndexingResult(1L, 5, true));

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트"));

        verify(stepService).markStarted(1L, ProcessingStep.RAG_INDEXING);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    @DisplayName("PolicyUpsertedEvent 본문 없으면 RAG_INDEXING SKIPPED 기록")
    void onPolicyUpserted_본문_없으면_RAG_INDEXING_SKIPPED_기록() {
        Policy policy = mock(Policy.class);
        when(policy.getBody()).thenReturn("");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(stepService.markStarted(1L, ProcessingStep.RAG_INDEXING)).thenReturn(100L);

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트"));

        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SKIPPED), eq("EMPTY_BODY"), isNull());
        verify(ragIndexingService, never()).indexPolicyDocument(any());
    }

    @Test
    @DisplayName("PolicyUpsertedEvent 정책 미존재 시 RAG_INDEXING SKIPPED(POLICY_NOT_FOUND) 기록")
    void onPolicyUpserted_정책_미존재_시_RAG_INDEXING_SKIPPED_기록() {
        when(policyRepository.findById(1L)).thenReturn(Optional.empty());
        when(stepService.markStarted(1L, ProcessingStep.RAG_INDEXING)).thenReturn(100L);

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트"));

        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SKIPPED), eq("POLICY_NOT_FOUND"), isNull());
        verify(ragIndexingService, never()).indexPolicyDocument(any());
    }

    @Test
    @DisplayName("PolicyUpsertedEvent 예외 시 RAG_INDEXING FAILED 기록 후 swallow")
    void onPolicyUpserted_예외_시_RAG_INDEXING_FAILED_기록_후_swallow() {
        Policy policy = mock(Policy.class);
        when(policy.getBody()).thenReturn("본문");
        when(policy.getEnrichment()).thenReturn(null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(stepService.markStarted(1L, ProcessingStep.RAG_INDEXING)).thenReturn(100L);
        when(ragIndexingService.indexPolicyDocument(any())).thenThrow(new RuntimeException("embedding 실패"));

        listener.onPolicyUpserted(new PolicyUpsertedEvent(1L, "테스트"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.FAILED), reasonCaptor.capture(), isNull());
        Assertions.assertThat(reasonCaptor.getValue()).contains("embedding 실패");
    }
}
