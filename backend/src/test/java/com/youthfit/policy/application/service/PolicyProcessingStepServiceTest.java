package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PolicyProcessingStepServiceTest {

    private PolicyProcessingStepRepository repository;
    private PolicyProcessingStepService service;

    @BeforeEach
    void setUp() {
        repository = mock(PolicyProcessingStepRepository.class);
        service = new PolicyProcessingStepService(repository);
    }

    @Test
    void markStarted_는_attempt_1_로_저장한다_기존_없을_때() {
        when(repository.countByPolicyIdAndStep(1L, ProcessingStep.GUIDE)).thenReturn(0);
        when(repository.save(any())).thenAnswer(invocation -> {
            PolicyProcessingStep saved = invocation.getArgument(0);
            return saved;
        });

        Long stepId = service.markStarted(1L, ProcessingStep.GUIDE);

        ArgumentCaptor<PolicyProcessingStep> captor = ArgumentCaptor.forClass(PolicyProcessingStep.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(ProcessingStatus.IN_PROGRESS);
    }

    @Test
    void markStarted_는_attempt_N_plus_1_로_저장한다_기존_있을_때() {
        when(repository.countByPolicyIdAndStep(1L, ProcessingStep.RAG_INDEXING)).thenReturn(2);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.markStarted(1L, ProcessingStep.RAG_INDEXING);

        ArgumentCaptor<PolicyProcessingStep> captor = ArgumentCaptor.forClass(PolicyProcessingStep.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isEqualTo(3);
    }

    @Test
    void markFinished_는_엔티티_finish_호출_후_save_한다() {
        PolicyProcessingStep existing = PolicyProcessingStep.start(1L, ProcessingStep.GUIDE, 1);
        when(repository.findById(100L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.markFinished(100L, ProcessingStatus.SUCCESS, null, null);

        ArgumentCaptor<PolicyProcessingStep> captor = ArgumentCaptor.forClass(PolicyProcessingStep.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(captor.getValue().getFinishedAt()).isNotNull();
    }

    @Test
    void markFinished_는_없는_id_면_경고만_남기고_종료된다() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        service.markFinished(999L, ProcessingStatus.FAILED, "no row", null);

        verify(repository, never()).save(any());
    }
}
